import { test } from 'node:test';
import assert from 'node:assert/strict';
import { consumeEvents } from '../src/app/stream.js';

const response = chunks => new Response(new ReadableStream({start(controller) { for(const chunk of chunks) controller.enqueue(chunk); controller.close(); }}));
test('SSE decodes split UTF-8, CRLF, multiline data, comments and event boundaries',async()=>{
  const bytes=new TextEncoder().encode(': heartbeat\r\nevent: token\r\ndata: café 🌱\r\ndata: second line\r\n\r\nevent: done\ndata: {"answer":"ok"}\n\n');
  const events=[];
  await consumeEvents(response(Array.from(bytes, b=>new Uint8Array([b]))),(name,data)=>events.push([name,data]));
  assert.deepEqual(events,[['token','café 🌱\nsecond line'],['done','{"answer":"ok"}']]);
});
test('incomplete event is not mistaken for a completed response',async()=>{
  const events=[];
  await consumeEvents(response([new TextEncoder().encode('event: done\ndata: {}')]),(...e)=>events.push(e));
  assert.deepEqual(events,[]);
});
test('callback failures cancel the stream and propagate',async()=>{
  let cancelled=false;
  const stream=new ReadableStream({start(c){c.enqueue(new TextEncoder().encode('event: sources\ndata: broken\n\n'));},cancel(){cancelled=true;}});
  await assert.rejects(consumeEvents(new Response(stream),()=>{throw new Error('invalid payload');}),/invalid payload/);
  assert.equal(cancelled,true);
});
