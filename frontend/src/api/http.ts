import axios, {
  AxiosError,
  type AxiosAdapter,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'

import type { ApiErrorBody, ApiResponse, CsrfData, FieldError } from '@/types/api'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 为 true 时 401 不触发“跳转登录”，用于启动时探测登录状态等场景。 */
    skipAuthRedirect?: boolean
    /** 内部使用：CSRF 失效后是否已经重试过一次。 */
    csrfRetried?: boolean
  }
}

export const CSRF_HEADER = 'X-CSRF-TOKEN'
const SAFE_METHODS = new Set(['get', 'head', 'options'])

/**
 * 统一的接口错误。页面按 code 或 status 判断，不依赖 message 文案。
 * 没有收到后端响应时，status 为 0，code 为 NETWORK_ERROR 或 TIMEOUT。
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly errors: FieldError[]
  readonly requestId: string | null

  constructor(
    status: number,
    code: string,
    message: string,
    errors: FieldError[] = [],
    requestId: string | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.errors = errors
    this.requestId = requestId
  }
}

export interface HttpClientOptions {
  /** 测试时替换网络层。 */
  adapter?: AxiosAdapter
}

export interface HttpClient {
  instance: AxiosInstance
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T>
  patch<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  /** 登录、退出、改密成功后调用：丢弃旧令牌并重新获取。 */
  refreshCsrfToken(): Promise<string>
  /** 设置 401（未登录或会话失效）时的处理，一般是清空当前用户并跳转登录页。 */
  onUnauthorized(handler: () => void): void
}

/**
 * 创建请求客户端：baseURL 固定 /api/v1，写请求自动携带 CSRF 令牌，错误统一转为 ApiError。
 * 令牌只放内存，不写 localStorage。
 */
export function createHttpClient(options: HttpClientOptions = {}): HttpClient {
  const instance = axios.create({
    baseURL: '/api/v1',
    timeout: 15000,
    adapter: options.adapter,
  })

  let csrfToken: string | null = null
  let csrfRequest: Promise<string> | null = null
  let unauthorizedHandler: (() => void) | null = null

  function ensureCsrfToken(): Promise<string> {
    if (csrfToken) {
      return Promise.resolve(csrfToken)
    }
    // 多个写请求同时发起时共用同一次令牌请求
    csrfRequest ??= instance
      .get<ApiResponse<CsrfData>>('/auth/csrf')
      .then((response) => {
        csrfToken = response.data.data.token
        return csrfToken
      })
      .finally(() => {
        csrfRequest = null
      })
    return csrfRequest
  }

  function refreshCsrfToken(): Promise<string> {
    csrfToken = null
    return ensureCsrfToken()
  }

  instance.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
    const method = (config.method ?? 'get').toLowerCase()
    if (!SAFE_METHODS.has(method)) {
      config.headers.set(CSRF_HEADER, await ensureCsrfToken())
    }
    return config
  })

  instance.interceptors.response.use(undefined, async (error: unknown) => {
    const apiError = toApiError(error)
    const config = error instanceof AxiosError ? error.config : undefined

    if (apiError.status === 401) {
      // 会话已失效，旧令牌也随之作废
      csrfToken = null
      if (!config?.skipAuthRedirect) {
        unauthorizedHandler?.()
      }
    }

    // CSRF 校验在写入前执行，被拒绝的请求没有生效，刷新令牌后最多重试一次
    if (apiError.code === 'CSRF_INVALID' && config && !config.csrfRetried) {
      config.csrfRetried = true
      await refreshCsrfToken()
      return instance.request(config)
    }

    throw apiError
  })

  async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
    const response = await promise
    return response.data.data
  }

  return {
    instance,
    get: (url, config) => unwrap(instance.get(url, config)),
    post: (url, body, config) => unwrap(instance.post(url, body, config)),
    put: (url, body, config) => unwrap(instance.put(url, body, config)),
    patch: (url, body, config) => unwrap(instance.patch(url, body, config)),
    delete: (url, config) => unwrap(instance.delete(url, config)),
    refreshCsrfToken,
    onUnauthorized(handler) {
      unauthorizedHandler = handler
    },
  }
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return (
    typeof value === 'object' && value !== null && typeof (value as ApiErrorBody).code === 'string'
  )
}

function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }
  if (!(error instanceof AxiosError)) {
    return new ApiError(0, 'UNKNOWN_ERROR', '发生未知错误')
  }
  const response = error.response
  if (!response) {
    return error.code === AxiosError.ECONNABORTED || error.code === AxiosError.ETIMEDOUT
      ? new ApiError(0, 'TIMEOUT', '请求超时，请稍后重试')
      : new ApiError(0, 'NETWORK_ERROR', '无法连接服务器，请检查网络或后端是否已启动')
  }
  if (isApiErrorBody(response.data)) {
    const body = response.data
    return new ApiError(response.status, body.code, body.message, body.errors ?? [], body.requestId)
  }
  // 后端未启动时开发代理返回 502/504 等非合同格式的响应
  return new ApiError(response.status, 'INTERNAL_ERROR', '服务暂时不可用，请稍后重试')
}

/** 全应用共用的唯一请求客户端；各模块的 api 文件都通过它发请求。 */
export const http = createHttpClient()
