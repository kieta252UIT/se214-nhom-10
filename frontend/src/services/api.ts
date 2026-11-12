import axios from "axios";

export const API_BASE_URL = import.meta.env.VITE_API_URL || "/api";
export const AUTH_BASE_URL = import.meta.env.VITE_AUTH_URL || "/auth";

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});
export const authApi = axios.create({
  baseURL: AUTH_BASE_URL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
});

// Request Interceptor: Chuẩn bị header nếu cần
api.interceptors.request.use(
  (config) => {
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: Xử lý tập trung mã lỗi HTTP và chuẩn hóa dữ liệu lỗi
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error: AxiosError<ApiErrorResponse>) => {
    const status = error.response?.status;
    const responseData = error.response?.data;

    const normalizedError: ApiErrorResponse = {
      status: status || 500,
      code: responseData?.code || (status === 401 ? "UNAUTHORIZED" : status === 403 ? "FORBIDDEN" : "INTERNAL_ERROR"),
      message: responseData?.message || error.message || "Đã có lỗi xảy ra. Vui lòng thử lại sau.",
      details: responseData?.details,
      timestamp: responseData?.timestamp || new Date().toISOString(),
    };

    // Khi nhận 401: phiên không hợp lệ hoặc đã bị thu hồi
    if (status === 401) {
      const { isAuthenticated, clearAuth } = useAuthStore.getState();
      if (isAuthenticated) {
        clearAuth();
      }
    }

    return Promise.reject(normalizedError);
  }
);

export default api;
