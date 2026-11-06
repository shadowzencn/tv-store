<?php

namespace App\Http;

use Illuminate\Http\Exceptions\ThrottleRequestsException;
use Illuminate\Http\JsonResponse;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;
use Throwable;

/**
 * Renders every error under /api as {"error": {"code": "...", "message": "..."}} (TDD section 5).
 * Reporting/logging is unaffected; Laravel still reports unexpected exceptions.
 */
class ApiErrorRenderer
{
    public static function render(Throwable $e): JsonResponse
    {
        if ($e instanceof ValidationException) {
            return self::json(422, 'validation_failed', $e->validator->errors()->first() ?: '请求参数无效。');
        }

        if ($e instanceof ThrottleRequestsException) {
            return self::json(429, 'too_many_requests', '请求过于频繁，请稍后再试。', $e->getHeaders());
        }

        if ($e instanceof HttpExceptionInterface) {
            $status = $e->getStatusCode();
            [$code, $default] = match ($status) {
                404 => ['not_found', '请求的资源不存在。'],
                405 => ['method_not_allowed', '不支持的请求方法。'],
                default => ['http_error', '请求无法处理。'],
            };
            $message = $status < 500 && $e->getMessage() !== '' && ! str_starts_with($e->getMessage(), 'The route ')
                ? $e->getMessage()
                : $default;

            return self::json($status, $code, $message, $e->getHeaders());
        }

        return self::json(500, 'server_error', '服务器内部错误。');
    }

    private static function json(int $status, string $code, string $message, array $headers = []): JsonResponse
    {
        return response()->json(
            ['error' => ['code' => $code, 'message' => $message]],
            $status,
            $headers,
            JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES,
        );
    }
}
