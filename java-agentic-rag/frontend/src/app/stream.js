// Incremental SSE parser: network chunks need not align with lines or events.
export async function consumeEvents(response, onEvent) {
  if (!response.body) throw new Error('Streaming is unavailable in this browser.');
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '', event = 'message', data = [], ended = false;
  const line = value => {
    if (value === '') {
      if (data.length) onEvent(event, data.join('\n'));
      event = 'message'; data = [];
    } else if (value.startsWith('event:')) event = value.slice(6).replace(/^ /, '');
    else if (value.startsWith('data:')) data.push(value.slice(5).replace(/^ /, ''));
  };
  try {
    while (true) {
      const {value, done} = await reader.read();
      buffer += done ? decoder.decode() : decoder.decode(value, {stream: true});
      let match;
      while ((match = /\r\n|\r|\n/.exec(buffer))) {
        if (!done && match[0] === '\r' && match.index === buffer.length - 1) break;
        line(buffer.slice(0, match.index));
        buffer = buffer.slice(match.index + match[0].length);
      }
      if (done) { ended = true; break; }
    }
  } finally {
    if (!ended) await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
