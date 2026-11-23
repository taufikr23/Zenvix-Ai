import { useState } from 'react';

function CodeBlock({ language, code }) {
  const [status, setStatus] = useState('Salin kode');
  async function copy() {
    try { await navigator.clipboard.writeText(code); setStatus('Tersalin'); }
    catch { setStatus('Gagal menyalin'); }
  }
  return <div className="code-block"><div className="code-header"><span>{language || 'Kode'}</span><button onClick={copy}>{status}</button></div><pre><code>{code}</code></pre></div>;
}

// Model-generated code stays inert text, including HTML and scripts.
export function AnswerContent({ text }) {
  const parts = text.split(/(```[^\n]*\n[\s\S]*?```)/g);
  return <div className="answer-text">{parts.map((part, index) => {
    const match = /^```([^\n]*)\n([\s\S]*?)```$/.exec(part);
    return match ? <CodeBlock key={index} language={match[1].trim()} code={match[2].replace(/\n$/, '')}/> : <span key={index}>{part}</span>;
  })}</div>;
}
