<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Presenters\ApiPresenter;
use App\Models\StoreApp;
use Illuminate\Http\JsonResponse;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

/**
 * The store client is managed like any other app (an `apps` row whose package name is
 * config('tvstore.client_package_name')); this returns its currently published version,
 * regardless of whether that app is listed in the catalog.
 */
class ClientVersionController extends Controller
{
    public function __invoke(): JsonResponse
    {
        $app = StoreApp::query()
            ->where('package_name', config('tvstore.client_package_name'))
            ->with('publishedVersion')
            ->first();

        if ($app?->publishedVersion === null) {
            throw new NotFoundHttpException('商店客户端暂无已发布版本。');
        }

        return response()->json(
            ApiPresenter::version($app->publishedVersion),
            200,
            ['Cache-Control' => 'no-cache'],
            JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES,
        );
    }
}
