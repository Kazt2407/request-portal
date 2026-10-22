// Load test for Request Portal: realistic reads plus full submit-and-approve flows through the gateway.
// Run with scripts/load-test.sh (k6 in Docker), against the k3d cluster or services started locally.
import http from 'k6/http';
import { check } from 'k6';

const API = __ENV.API_URL || 'http://localhost:8088/api/v1';
const KEYCLOAK = __ENV.KEYCLOAK_URL || 'http://localhost:8180';
const DURATION = __ENV.DURATION || '2m';
const READ_RATE = Number(__ENV.READ_RATE || 30);
const FLOW_RATE = Number(__ENV.FLOW_RATE || 5);

export const options = {
  scenarios: {
    // Students and staff looking at lists: the bulk of real traffic.
    browse: {
      executor: 'constant-arrival-rate',
      exec: 'browse',
      rate: READ_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 20,
      maxVUs: 80,
    },
    // A student submits a request and Academic Affairs completes it: 2 writes, 3 events, 3 emails each.
    submitAndApprove: {
      executor: 'constant-arrival-rate',
      exec: 'submitAndApprove',
      rate: FLOW_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 10,
      maxVUs: 40,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{kind:read}': ['p(95)<300'],
    'http_req_duration{kind:write}': ['p(95)<800'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function signIn(username) {
  const response = http.post(`${KEYCLOAK}/realms/request-portal/protocol/openid-connect/token`, {
    grant_type: 'password',
    client_id: 'portal-dev',
    username,
    password: 'password',
  });
  check(response, { 'signed in': (r) => r.status === 200 });
  return response.json('access_token');
}

function headers(token, extra = {}) {
  return { headers: Object.assign({ Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, extra) };
}

function uuid() {
  const hex = () => Math.floor(Math.random() * 16).toString(16);
  return 'xxxxxxxx-xxxx-4xxx-8xxx-xxxxxxxxxxxx'.replace(/x/g, hex);
}

export function setup() {
  const student = signIn('student');
  const staff = signIn('staff.academic');
  const templates = http.get(`${API}/templates/available`, headers(student)).json();
  const template = templates.find((t) => t.title === 'Student status confirmation');
  return { student, staff, templateId: template.id };
}

export function browse(data) {
  const read = { tags: { kind: 'read' } };
  const pick = Math.random();
  let response;
  if (pick < 0.4) {
    response = http.get(`${API}/templates/available`, Object.assign(headers(data.student), read));
  } else if (pick < 0.75) {
    response = http.get(`${API}/requests/mine?size=20`, Object.assign(headers(data.student), read));
  } else {
    response = http.get(`${API}/requests?awaitingMe=true&size=20`, Object.assign(headers(data.staff), read));
  }
  check(response, { 'read ok': (r) => r.status === 200 });
}

export function submitAndApprove(data) {
  const write = { tags: { kind: 'write' } };
  const submitted = http.post(
    `${API}/requests`,
    JSON.stringify({ templateId: data.templateId, values: { purpose: 'Student loan', term: '2026-2027-1' } }),
    Object.assign(headers(data.student, { 'Idempotency-Key': uuid() }), write),
  );
  if (!check(submitted, { 'submitted': (r) => r.status === 201 })) {
    return;
  }
  const completed = http.post(
    `${API}/requests/${submitted.json('id')}/steps/1/complete`,
    JSON.stringify({ note: 'Confirmed by load test' }),
    Object.assign(headers(data.staff), write),
  );
  check(completed, { 'approved': (r) => r.status === 200 });
}
