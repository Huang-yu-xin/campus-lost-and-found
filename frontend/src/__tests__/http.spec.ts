import { describe, expect, it, vi } from 'vitest'
import {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'

import { ApiError, CSRF_HEADER, createHttpClient } from '@/api/http'

// 用假的网络层驱动请求客户端，验证 CSRF 携带、重试和错误转换规则

type Handler = (config: InternalAxiosRequestConfig) => { status: number; data: unknown }

function fakeAdapter(handler: Handler) {
  const calls: InternalAxiosRequestConfig[] = []
  const adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    calls.push(config)
    const { status, data } = handler(config)
    const response: AxiosResponse = { status, statusText: '', data, headers: {}, config }
    if (status >= 400) {
      throw new AxiosError('failed', AxiosError.ERR_BAD_RESPONSE, config, null, response)
    }
    return response
  }
  return { adapter, calls }
}

const ok = (data: unknown) => ({
  status: 200,
  data: { code: 'OK', message: '操作成功', data, requestId: 'req-1' },
})
const fail = (status: number, code: string) => ({
  status,
  data: { code, message: `${code} 错误`, data: null, requestId: 'req-2', errors: [] },
})

function csrfHeader(config: InternalAxiosRequestConfig) {
  return AxiosHeaders.from(config.headers).get(CSRF_HEADER)
}

describe('createHttpClient', () => {
  it('写请求前获取 CSRF 令牌并放入请求头，读请求不需要令牌', async () => {
    const { adapter, calls } = fakeAdapter((config) =>
      config.url === '/auth/csrf'
        ? ok({ headerName: CSRF_HEADER, token: 'token-1' })
        : ok({ done: true }),
    )
    const client = createHttpClient({ adapter })

    await client.get('/items')
    const result = await client.post<{ done: boolean }>('/items', { title: '校园卡' })

    expect(result).toEqual({ done: true })
    expect(calls.map((c) => c.url)).toEqual(['/items', '/auth/csrf', '/items'])
    expect(csrfHeader(calls[0]!)).toBeUndefined()
    expect(csrfHeader(calls[2]!)).toBe('token-1')
  })

  it('并发写请求只获取一次令牌', async () => {
    const { adapter, calls } = fakeAdapter((config) =>
      config.url === '/auth/csrf' ? ok({ headerName: CSRF_HEADER, token: 'token-1' }) : ok({}),
    )
    const client = createHttpClient({ adapter })

    await Promise.all([client.post('/a'), client.post('/b'), client.patch('/c')])

    expect(calls.filter((c) => c.url === '/auth/csrf')).toHaveLength(1)
  })

  it('CSRF 失效时刷新令牌并只重试一次', async () => {
    let tokenNo = 0
    let writes = 0
    const { adapter } = fakeAdapter((config) => {
      if (config.url === '/auth/csrf') {
        tokenNo += 1
        return ok({ headerName: CSRF_HEADER, token: `token-${tokenNo}` })
      }
      writes += 1
      return csrfHeader(config) === 'token-2' ? ok({ saved: true }) : fail(403, 'CSRF_INVALID')
    })
    const client = createHttpClient({ adapter })

    await expect(client.post('/items')).resolves.toEqual({ saved: true })
    expect(writes).toBe(2)
  })

  it('重试后仍然 CSRF 失效则抛出错误，不会无限重试', async () => {
    let writes = 0
    const { adapter } = fakeAdapter((config) => {
      if (config.url === '/auth/csrf') {
        return ok({ headerName: CSRF_HEADER, token: 'token' })
      }
      writes += 1
      return fail(403, 'CSRF_INVALID')
    })
    const client = createHttpClient({ adapter })

    await expect(client.post('/items')).rejects.toMatchObject({ status: 403, code: 'CSRF_INVALID' })
    expect(writes).toBe(2)
  })

  it('普通 409 不自动重发写请求，并转为带错误码的 ApiError', async () => {
    let writes = 0
    const { adapter } = fakeAdapter((config) => {
      if (config.url === '/auth/csrf') {
        return ok({ headerName: CSRF_HEADER, token: 'token' })
      }
      writes += 1
      return fail(409, 'ITEM_STATE_CONFLICT')
    })
    const client = createHttpClient({ adapter })

    const error = await client.post('/items').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, code: 'ITEM_STATE_CONFLICT', requestId: 'req-2' })
    expect(writes).toBe(1)
  })

  it('401 触发未登录处理；skipAuthRedirect 的请求不触发', async () => {
    const { adapter } = fakeAdapter(() => fail(401, 'AUTH_REQUIRED'))
    const client = createHttpClient({ adapter })
    const onUnauthorized = vi.fn<() => void>()
    client.onUnauthorized(onUnauthorized)

    await expect(client.get('/me', { skipAuthRedirect: true })).rejects.toMatchObject({
      status: 401,
    })
    expect(onUnauthorized).not.toHaveBeenCalled()

    await expect(client.get('/me/items')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('后端未启动（代理返回非合同格式）时给出可读的错误', async () => {
    const { adapter } = fakeAdapter(() => ({ status: 502, data: 'Bad Gateway' }))
    const client = createHttpClient({ adapter })

    await expect(client.get('/items')).rejects.toMatchObject({
      status: 502,
      code: 'INTERNAL_ERROR',
    })
  })
})
