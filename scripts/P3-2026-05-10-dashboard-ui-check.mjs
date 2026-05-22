import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';

const html = readFileSync('token-meter-app/src/main/resources/static/index.html', 'utf8');
const js = readFileSync('token-meter-app/src/main/resources/static/app.js', 'utf8');
const css = readFileSync('token-meter-app/src/main/resources/static/app.css', 'utf8');

assert.match(js, /teamSection:\s*'overview'/, 'Team view must default to the Overview tab');
assert.match(js, /localSection:\s*'overview'/, 'Local view must default to the Overview tab');
assert.match(html, /data-team-section-tab="overview"/, 'Team Overview tab is required');
assert.match(html, /data-local-section-tab="overview"/, 'Local Overview tab is required');
assert.match(html, />All Teams</, 'Team selector must expose All Teams as the default aggregation');
assert.match(html, /data-team-section="overview"/, 'Team Overview section is required');
assert.match(js, /period=\$\{encodeURIComponent\(periodForView\(\)\)\}&compare=previous/, 'Views must request selected period comparison data');
assert.match(js, /teamPeriod:\s*'day'/, 'Team view must default to the Day period');
assert.match(js, /function queryForView\(\)/, 'Request query must be separate from selected period state');
assert.match(html, /data-period="day"/, 'Day period control is required');
assert.match(html, /data-period="week"/, 'Week period control is required');
assert.match(html, /data-period="month"/, 'Month period control is required');
assert.match(html, /Period Comparison/, 'Team Overview must lead with period comparison language');
assert.match(css, /\.app-shell/, 'Dashboard shell styling is required');
assert.match(css, /\.side-nav\s*\{[^}]*position:\s*fixed/s, 'Desktop side navigation must stay fixed when Team tabs switch');
assert.match(css, /\.side-nav\s*\{[^}]*left:\s*max\(16px,\s*calc\(\(100vw - 1360px\) \/ 2\)\)/s, 'Fixed side navigation must align to the dashboard shell');
assert.match(css, /\.content\s*\{[^}]*grid-column:\s*2/s, 'Content must stay in the second dashboard column beside fixed navigation');
assert.doesNotMatch(css, /\.side-nav\s*\{[^}]*position:\s*sticky/s, 'Desktop side navigation must not use sticky positioning');
assert.doesNotMatch(html, /prompt|response|raw JSONL|device token|token hash/i, 'Dashboard UI must not expose private payload terms');
