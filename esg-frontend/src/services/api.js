const API_BASE = 'http://localhost:8080/api/v1';

let authToken = null;

export const setToken = (token) => { authToken = token; };
export const getToken = () => authToken;

async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (authToken) headers['Authorization'] = `Bearer ${authToken}`;

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || `API error ${res.status}`);
  return data;
}

export const api = {
  auth: {
    login: (email, password) => request('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
    me: () => request('/auth/me'),
  },
  dataHub: {
    list: (params = '') => request(`/data/points${params ? '?' + params : ''}`),
    create: (data) => request('/data/points', { method: 'POST', body: JSON.stringify(data) }),
    validate: (id, status) => request(`/data/points/${id}/validate`, { method: 'PUT', body: JSON.stringify({ status }) }),
  },
  carbon: {
    emissions: (params = '') => request(`/carbon/emissions${params ? '?' + params : ''}`),
    create: (data) => request('/carbon/emissions', { method: 'POST', body: JSON.stringify(data) }),
    summary: () => request('/carbon/emissions/summary'),
    factors: (region) => request(`/carbon/factors${region ? '?region=' + region : ''}`),
  },
  compliance: {
    frameworks: (jurisdiction) => request(`/compliance/frameworks${jurisdiction ? '?jurisdiction=' + jurisdiction : ''}`),
    gaps: (framework, year = 2026) => request(`/compliance/gaps?framework=${framework}&year=${year}`),
  },
};

export default api;
