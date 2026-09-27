import { createContext, useContext, useEffect, useState, useCallback } from "react";
import client from "../api/client";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const raw = localStorage.getItem("teamflow_user");
    return raw ? JSON.parse(raw) : null;
  });
  const [ready, setReady] = useState(true);

  const login = useCallback(async (email, password) => {
    const response = await client.post("/auth/login", { email, password });
    const { token, userId, name, role } = response.data;
    localStorage.setItem("teamflow_token", token);
    const nextUser = { id: userId, name, email, role };
    localStorage.setItem("teamflow_user", JSON.stringify(nextUser));
    setUser(nextUser);
    return nextUser;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem("teamflow_token");
    localStorage.removeItem("teamflow_user");
    setUser(null);
  }, []);

  useEffect(() => setReady(true), []);

  return (
    <AuthContext.Provider value={{ user, login, logout, ready, isManager: user?.role === "PROJECT_MANAGER" }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
