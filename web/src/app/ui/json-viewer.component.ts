import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type JsonTokenKind = 'key' | 'string' | 'number' | 'boolean' | 'null' | 'punct' | 'space';
export interface JsonToken {
  kind: JsonTokenKind;
  text: string;
}

const TOKEN_RE =
  /("(?:\\u[a-fA-F0-9]{4}|\\[^u]|[^\\"])*")(\s*:)?|\b(true|false)\b|\bnull\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|[{}[\],:]|\s+/g;

/**
 * Tokenizes pretty-printed JSON for syntax highlighting. Rendering is done with text
 * interpolation (no innerHTML), so arbitrary payloads can never inject markup.
 */
export function tokenizeJson(json: string): JsonToken[] {
  const tokens: JsonToken[] = [];
  let last = 0;
  for (const m of json.matchAll(TOKEN_RE)) {
    const index = m.index ?? 0;
    if (index > last) tokens.push({ kind: 'punct', text: json.slice(last, index) });
    const [full, str, colon, bool] = m;
    if (str !== undefined) {
      tokens.push({ kind: colon ? 'key' : 'string', text: str });
      if (colon) tokens.push({ kind: 'punct', text: colon });
    } else if (bool !== undefined) {
      tokens.push({ kind: 'boolean', text: full });
    } else if (full === 'null') {
      tokens.push({ kind: 'null', text: full });
    } else if (/^\s+$/.test(full)) {
      tokens.push({ kind: 'space', text: full });
    } else if (/^[{}[\],:]$/.test(full)) {
      tokens.push({ kind: 'punct', text: full });
    } else {
      tokens.push({ kind: 'number', text: full });
    }
    last = index + full.length;
  }
  if (last < json.length) tokens.push({ kind: 'punct', text: json.slice(last) });
  return tokens;
}

const KIND_CLASS: Record<JsonTokenKind, string> = {
  key: 'text-sky-700 dark:text-sky-300',
  string: 'text-emerald-700 dark:text-emerald-300',
  number: 'text-amber-700 dark:text-amber-300',
  boolean: 'text-violet-700 dark:text-violet-300',
  null: 'text-rose-700 dark:text-rose-300',
  punct: 'text-muted-foreground',
  space: '',
};

/** Lightweight JSON syntax highlighter (no dependencies). Large payloads are truncated. */
@Component({
  selector: 'app-json-viewer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pre
      class="bg-muted/40 max-h-[32rem] overflow-auto rounded-lg border p-4 font-mono text-xs leading-relaxed"
      tabindex="0"
      [attr.aria-label]="ariaLabel()"
    ><code>@for (t of tokens(); track $index) {<span [class]="cls[t.kind]">{{ t.text }}</span>}</code></pre>
    @if (truncated()) {
      <p class="text-muted-foreground mt-2 text-xs">Preview truncated to {{ maxChars() | number }} characters — download for the full document.</p>
    }
  `,
  imports: [DecimalPipe],
})
export class JsonViewerComponent {
  readonly value = input.required<unknown>();
  readonly maxChars = input(60_000);
  readonly ariaLabel = input('JSON document');

  protected readonly cls = KIND_CLASS;
  private readonly text = computed(() => {
    const v = this.value();
    return typeof v === 'string' ? v : (JSON.stringify(v, null, 2) ?? 'undefined');
  });
  protected readonly truncated = computed(() => this.text().length > this.maxChars());
  protected readonly tokens = computed(() => {
    const text = this.text();
    return tokenizeJson(this.truncated() ? `${text.slice(0, this.maxChars())}\n…` : text);
  });
}
