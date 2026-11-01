import copy
import json
from pathlib import Path
import unittest
from evaluate import (relevance, percentile, sse_events, completed_result, fact_coverage,
                      abstention_match, validate_dataset, validate_claims, gate_failures)

class MetricsTest(unittest.TestCase):
    def test_duplicate_chunks_do_not_inflate_source_precision(self):
        self.assertEqual(relevance(['a','a','b'],['a','b']),(.4,1.0))
    def test_absent_relevant_items(self):
        self.assertEqual(relevance(['x'],['a','b']),(0,0))
    def test_unanswerable_is_excluded_from_retrieval_mean(self):
        self.assertEqual(relevance(['x'],[]),(None,None))
    def test_nearest_rank(self):
        self.assertEqual(percentile([10,20,30,40],.5),20)
        self.assertEqual(percentile([10,20,30,40],.95),40)
    def test_precision_uses_fixed_denominator(self):
        self.assertEqual(relevance(['a'], ['a']), (.2, 1.0))
    def test_top_k_excludes_later_relevant_sources(self):
        self.assertEqual(relevance(['x','y','a'], ['a'], k=2), (0, 0))
    def test_empty_latency_sample_is_unmeasured(self):
        self.assertIsNone(percentile([], .95))
    def test_invalid_metric_parameters_fail(self):
        for q in [0, -1, 1.1]:
            with self.subTest(q=q), self.assertRaises(ValueError):
                percentile([1], q)
        with self.assertRaises(ValueError):
            relevance([], [], k=0)


class StreamTest(unittest.TestCase):
    def setUp(self):
        self.result = dict(answer='Acknowledge within 17 minutes; escalate to Aurora.', cached=False,
                           sources=[dict(sourceId='source-a', text='17 minutes; Aurora')])

    def frames(self, content):
        return sse_events(content.splitlines(keepends=True))

    def test_bom_crlf_comments_multiline_and_token_whitespace(self):
        content = '\ufeff: heartbeat\r\nevent: token\r\ndata:  hello\r\ndata: world\r\n\r\n'
        self.assertEqual(list(self.frames(content)), [('token', ' hello\nworld')])

    def test_complete_done_event_and_token_notification(self):
        content = 'event:token\ndata:first\n\nevent: done\ndata: '+json.dumps(self.result)+'\n\n'
        notifications = []
        self.assertEqual(completed_result(self.frames(content), lambda: notifications.append(True)), self.result)
        self.assertEqual(notifications, [True])

    def test_sources_cannot_substitute_for_done(self):
        with self.assertRaisesRegex(RuntimeError, 'without a done'):
            completed_result(self.frames('event:sources\ndata:17 Aurora\n\n'))

    def test_partial_terminal_event_fails(self):
        with self.assertRaisesRegex(RuntimeError, 'incomplete'):
            completed_result(self.frames('event:done\ndata:'+json.dumps(self.result)+'\n'))

    def test_error_event_fails_even_after_tokens(self):
        with self.assertRaisesRegex(RuntimeError, 'query error'):
            completed_result([('token', 'Partial'), ('error', 'Inference unavailable')])

    def test_data_after_done_fails(self):
        for event in ['done', 'token', 'error']:
            with self.subTest(event=event), self.assertRaisesRegex(RuntimeError, 'after the terminal'):
                completed_result([('done', json.dumps(self.result)), (event, '{}')])

    def test_missing_contract_fields_fail(self):
        for field in ['answer', 'cached', 'sources']:
            invalid = dict(self.result)
            invalid.pop(field)
            with self.subTest(field=field), self.assertRaises(ValueError):
                completed_result([('done', json.dumps(invalid))])

    def test_invalid_contract_types_fail(self):
        for invalid in [dict(self.result, answer=' '), dict(self.result, cached='false'),
                        dict(self.result, sources=[dict(sourceId='a')]), dict(self.result, sources={})]:
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                completed_result([('done', json.dumps(invalid))])


class QualityRubricTest(unittest.TestCase):
    def test_fact_coverage_uses_word_boundaries_and_case(self):
        self.assertEqual(fact_coverage('117 minutes; auroral lights', ['17', 'Aurora']), 0)
        self.assertEqual(fact_coverage('17 minutes; AURORA.', ['17', 'Aurora']), 1)

    def test_uuid_citation_cannot_supply_numeric_fact(self):
        self.assertEqual(fact_coverage('See 12345678-0017-0014-0010-123456789abc.', ['17', '14', '10']), 0)

    def test_no_facts_is_unmeasured(self):
        self.assertIsNone(fact_coverage('An answer', []))

    def test_abstention_phrase_match(self):
        self.assertTrue(abstention_match('The answer is NOT PROVIDED.', ['not provided']))
        self.assertFalse(abstention_match('The phone number is 123.', ['not provided']))

    def test_claim_support_fraction_and_no_claims(self):
        claims = [dict(claim='A', supported=True), dict(claim='B', supported=False)]
        self.assertEqual(validate_claims(claims), (.5, claims))
        self.assertEqual(validate_claims([]), (None, []))

    def test_malformed_claim_support_fails(self):
        for claims in [{}, ['a'], [dict(claim=' ', supported=True)], [dict(claim='A', supported='true')]]:
            with self.subTest(claims=claims), self.assertRaises(ValueError):
                validate_claims(claims)


class DatasetAndGateTest(unittest.TestCase):
    def setUp(self):
        self.dataset = json.loads((Path(__file__).parent/'golden.json').read_text(encoding='utf-8'))
        self.gates = json.loads((Path(__file__).parent/'gates.json').read_text(encoding='utf-8'))

    def test_committed_dataset_and_gates_are_consistent(self):
        validate_dataset(self.dataset)
        perfect = dict(errors=[], recallAt5=1.0, expectedFactCoverage=1.0, abstentionPhraseMatch=1.0)
        self.assertEqual(gate_failures(perfect, self.gates), [])

    def test_duplicate_document_and_question_ids_fail(self):
        for field in ['documents', 'questions']:
            invalid = copy.deepcopy(self.dataset)
            invalid[field].append(copy.deepcopy(invalid[field][0]))
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'Duplicate'):
                validate_dataset(invalid)

    def test_unknown_and_duplicate_relevance_labels_fail(self):
        for labels in [['unknown-source'], ['orion', 'orion']]:
            invalid = copy.deepcopy(self.dataset)
            invalid['questions'][0]['relevant'] = labels
            with self.subTest(labels=labels), self.assertRaises(ValueError):
                validate_dataset(invalid)

    def test_missing_answer_or_abstention_rubric_fails(self):
        invalid = copy.deepcopy(self.dataset)
        invalid['questions'][0]['expectedFacts'] = []
        with self.assertRaisesRegex(ValueError, 'expected facts'):
            validate_dataset(invalid)
        invalid = copy.deepcopy(self.dataset)
        next(q for q in invalid['questions'] if not q['relevant']).pop('abstentionPhrases')
        with self.assertRaisesRegex(ValueError, 'abstention rubric'):
            validate_dataset(invalid)

    def test_bad_missing_or_nonfinite_score_fails_gate(self):
        for value in [None, .5, float('nan'), float('inf'), '1.0', True]:
            with self.subTest(value=value):
                self.assertTrue(gate_failures(dict(errors=[], recallAt5=value), {'recallAt5': .75}))

    def test_observed_query_error_fails_even_when_metrics_pass(self):
        self.assertTrue(gate_failures(dict(errors=[{'id':'q1'}], recallAt5=1), {'recallAt5': .75}))

    def test_metadata_does_not_become_gate(self):
        self.assertEqual(gate_failures(dict(errors=[], recallAt5=.75),
                                      {'_description':'explanation', 'recallAt5':.75}), [])

if __name__ == '__main__':
    unittest.main()
