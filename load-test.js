import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 20 },
    { duration: '30s', target: 0 },
  ],
};

export default function () {
  const res = http.get('https://aura-edge-service.quantumcoin.workers.dev/health');
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(1);
}
