import axios from "axios";

// Never hard-code the backend origin: VITE_API_BASE_URL is baked in at
// build time from the environment, so the same build works unchanged in
// LOCAL / TEST / PRODUCTION (see frontend/.env.example).
const baseURL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

const client = axios.create({ baseURL });

client.interceptors.request.use((config) => {
  const token = localStorage.getItem("teamflow_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem("teamflow_token");
      localStorage.removeItem("teamflow_user");
      if (!window.location.pathname.startsWith("/login")) {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);

// Consistently unwrap the backend's ApiError shape into a plain message
// string so every screen can show the same "Unable to ..." pattern
// without repeating this logic.
export function extractErrorMessage(error, fallback) {
  return error?.response?.data?.message || fallback || "Something went wrong. Please try again.";
}

export default client;
