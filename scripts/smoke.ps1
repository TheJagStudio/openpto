# End-to-end smoke test through the gateway. Requires the stack to be up (.\dev.ps1 up).
param([string]$Base = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$fail = 0
function Check($name, [scriptblock]$body) {
  try { $r = & $body; Write-Host ("PASS  {0}  {1}" -f $name, $r) -ForegroundColor Green }
  catch { $script:fail++; Write-Host ("FAIL  {0}  {1}" -f $name, $_.Exception.Message) -ForegroundColor Red }
}
function J($method, $path, $body = $null, $headers = @{}) {
  $p = @{ Method = $method; Uri = "$Base$path"; Headers = $headers; ContentType = 'application/json' }
  if ($body) { $p.Body = ($body | ConvertTo-Json -Depth 10) }
  Invoke-RestMethod @p
}

Check 'gateway status' { $s = J GET '/gateway/status'; ($s.services | % { "$($_.name)=$($_.status)" }) -join ' ' }
Check 'stats' { $s = J GET '/api/v1/stats'; "patents=$($s.patents) trademarks=$($s.trademarks)" }
Check 'patent search' { $p = J GET '/api/v1/patents?q=battery&size=3'; "total=$($p.totalElements) first=$($p.content[0].patentNumber)" }
Check 'patent detail' { $n = (J GET '/api/v1/patents?size=1').content[0].patentNumber; $d = J GET "/api/v1/patents/$n"; "$n claims=$($d.claims.Count)" }
Check 'trademark search' { $t = J GET '/api/v1/trademarks?q=coffee&size=2'; "total=$($t.totalElements)" }
Check 'fee schedules' { $s = @(J GET '/api/v1/fees/schedules'); ($s | ForEach-Object { $_ } | Select-Object -ExpandProperty code) -join ',' }
Check 'patent filing fee' {
  $q = J POST '/api/v1/fees/patent/filing' @{ applicationType='UTILITY'; entitySize='SMALL'; totalClaims=25; independentClaims=5; multipleDependentClaims=$false; specificationSheets=40; filedElectronically=$true; lateFilingSurcharge=$false; extensionMonths=0; continuedExamination='NONE'; prioritizedExamination=$false; filingDate='2025-06-01' }
  if ($q.total -ne 1680) { throw "expected 1680, got $($q.total)" }; "total=$($q.total)"
}
Check 'internal blocked' { try { J GET '/internal/v1/usage' | Out-Null; throw 'reachable!' } catch { if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw } ; '404' } }

$email = "smoke$(Get-Random)@example.com"
$script:token = $null; $script:key = $null
Check 'register' { $a = J POST '/api/v1/auth/register' @{ email=$email; password='Smoke12345x'; displayName='Smoke' }; $script:token = $a.accessToken; $a.user.email }
$auth = @{ Authorization = "Bearer $script:token" }
Check 'create api key' { $k = J POST '/api/v1/account/api-keys' @{ name='smoke' } $auth; $script:key = $k.key; $k.prefix }
Check 'search with api key (FREE tier)' {
  $r = Invoke-WebRequest -UseBasicParsing "$Base/api/v1/patents?size=1" -Headers @{ 'X-API-Key' = $script:key }
  "limit=$($r.Headers['X-RateLimit-Limit']) remaining=$($r.Headers['X-RateLimit-Remaining'])"
}
Check 'invalid api key -> 401' { try { Invoke-WebRequest -UseBasicParsing "$Base/api/v1/patents" -Headers @{ 'X-API-Key'='opto_bogus' } | Out-Null; throw 'accepted!' } catch { if ($_.Exception.Response.StatusCode.value__ -ne 401) { throw }; '401' } }

Check 'ingest upload + pipeline' {
  $tmp = Join-Path $env:TEMP 'grant-sample.xml'
  $samples = J GET '/api/v1/ingest/samples'
  $name = ($samples | ? { $_.format -eq 'US_PATENT_GRANT' } | select -First 1).name
  Invoke-WebRequest -UseBasicParsing "$Base/api/v1/ingest/samples/$name" -OutFile $tmp
  $up = curl.exe -s -H "Authorization: Bearer $script:token" -F "files=@$tmp;type=application/xml" "$Base/api/v1/ingest/uploads" | ConvertFrom-Json
  $id = $up[0].id
  for ($i = 0; $i -lt 30; $i++) { $j = J GET "/api/v1/ingest/jobs/$id" $null $auth; if ($j.status -in 'COMPLETED','PARTIAL','FAILED') { break }; Start-Sleep 1 }
  if ($j.status -ne 'COMPLETED') { throw "job $($j.status)" }
  "job=$id loaded=$($j.recordsLoaded)/$($j.recordsTotal)"
}
Check 'aggregated api docs' { (J GET '/v3/api-docs/fees').info.title }

if ($fail) { Write-Host "$fail check(s) failed" -ForegroundColor Red; exit 1 } else { Write-Host 'all checks passed' -ForegroundColor Green }
