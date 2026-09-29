// Aggregated Swagger UI: one spec per downstream, each served (and rewritten) by the gateway.
window.addEventListener('load', function () {
  window.ui = SwaggerUIBundle({
    urls: [
      { url: '/v3/api-docs/odp', name: 'Open Data + accounts (odp-service)' },
      { url: '/v3/api-docs/fees', name: 'Fee calculator (fee-service)' },
      { url: '/v3/api-docs/ingest', name: 'Ingest pipeline (ingest-service)' }
    ],
    'urls.primaryName': 'Open Data + accounts (odp-service)',
    dom_id: '#swagger-ui',
    deepLinking: true,
    persistAuthorization: true,
    validatorUrl: null,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    plugins: [SwaggerUIBundle.plugins.DownloadUrl],
    layout: 'StandaloneLayout'
  });
});
