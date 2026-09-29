import type { CodeSample } from '../../ui/code-tabs.component';

/** Code snippets for the four supported languages, using the caller's key placeholder. */
export function apiSnippets(origin: string, keyPlaceholder: string): CodeSample[] {
  const url = `${origin}/api/v1/patents?q=solar+cell&status=GRANTED&size=5`;
  return [
    {
      id: 'curl',
      label: 'curl',
      code: `curl -s "${url}" \\
  -H "X-API-Key: ${keyPlaceholder}" \\
  -H "Accept: application/json"`,
    },
    {
      id: 'js',
      label: 'JavaScript (fetch)',
      code: `const res = await fetch("${url}", {
  headers: { "X-API-Key": "${keyPlaceholder}", Accept: "application/json" },
});
if (!res.ok) {
  const problem = await res.json(); // RFC 7807: { title, detail, status, requestId }
  throw new Error(\`\${problem.title}: \${problem.detail} (request \${problem.requestId})\`);
}
console.log("remaining this minute:", res.headers.get("X-RateLimit-Remaining"));
const { content, totalElements } = await res.json();`,
    },
    {
      id: 'python',
      label: 'Python (requests)',
      code: `import requests

resp = requests.get(
    "${origin}/api/v1/patents",
    params={"q": "solar cell", "status": "GRANTED", "size": 5},
    headers={"X-API-Key": "${keyPlaceholder}"},
    timeout=10,
)
resp.raise_for_status()
for patent in resp.json()["content"]:
    print(patent["patentNumber"], patent["title"])`,
    },
    {
      id: 'java',
      label: 'Java (HttpClient)',
      code: `var client = HttpClient.newHttpClient();
var request = HttpRequest.newBuilder(URI.create("${url}"))
    .header("X-API-Key", "${keyPlaceholder}")
    .header("Accept", "application/json")
    .timeout(Duration.ofSeconds(10))
    .build();
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.statusCode() + " " + response.body());`,
    },
  ];
}
