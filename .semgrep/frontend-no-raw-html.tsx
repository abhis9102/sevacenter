// Test cases for frontend-no-raw-html.yaml. Run: semgrep --test .semgrep/
// "ruleid:" = the rule must flag the next line; "ok:" = it must not. Not compiled, not scanned
// (CI's scan runs with --exclude .semgrep); it only has to parse. Keep each JSX case on one line:
// Semgrep reports a multi-line JSX match on its first line, and ignores markers inside JSX.

export function Unsafe({ purpose, html }: { purpose: string; html: string }) {
  const el = document.getElementById("x")!;
  // ruleid: frontend-no-raw-html
  el.innerHTML = purpose;
  // ruleid: frontend-no-raw-html
  el.outerHTML = html;
  // ruleid: frontend-no-raw-html
  el.insertAdjacentHTML("beforeend", purpose);
  // ruleid: frontend-no-raw-html
  document.write(html);
  // ruleid: frontend-no-raw-html
  eval(purpose);
  // ruleid: frontend-no-raw-html
  const f = new Function(purpose);
  // ruleid: frontend-no-raw-html
  const frag = document.createRange().createContextualFragment(html);
  // A props object (spread later) doesn't hide it.
  // ruleid: frontend-no-raw-html
  const props = { className: "note", dangerouslySetInnerHTML: { __html: purpose } };
  // ruleid: frontend-no-raw-html
  const selfClosing = <p dangerouslySetInnerHTML={{ __html: purpose }} />;
  // Other attributes around it don't hide it either.
  // ruleid: frontend-no-raw-html
  const withChildren = <span className="x" dangerouslySetInnerHTML={{ __html: html }}></span>;
  return <div>{selfClosing}{withChildren}</div>;
}

export function Safe({ purpose }: { purpose: string }) {
  const el = document.getElementById("x")!;
  // Text, not markup.
  // ok: frontend-no-raw-html
  el.textContent = purpose;
  // Reading innerHTML is not a sink.
  // ok: frontend-no-raw-html
  const current = el.innerHTML;
  // Parsing data is not running code.
  // ok: frontend-no-raw-html
  const evaluated = JSON.parse(purpose);
  // React escapes text children: how every stored field is rendered today.
  // ok: frontend-no-raw-html
  return <td className="text-muted">{purpose}</td>;
}
