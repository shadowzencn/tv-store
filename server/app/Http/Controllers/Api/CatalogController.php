<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Presenters\ApiPresenter;
use App\Models\Category;
use App\Models\StoreApp;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class CatalogController extends Controller
{
    public function __invoke(Request $request): JsonResponse
    {
        $categories = Category::query()->orderBy('sort_order')->orderBy('id')->get();

        $apps = StoreApp::query()
            ->where('is_listed', true)
            ->whereHas('publishedVersion')
            ->with('publishedVersion')
            ->orderBy('sort_order')
            ->orderBy('id')
            ->get();

        $response = response()->json([
            'categories' => $categories->map(ApiPresenter::category(...))->values(),
            'apps' => $apps->map(ApiPresenter::app(...))->values(),
        ], 200, [], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);

        // The ETag is derived from the body, so any change to the catalog changes it.
        $response->setEtag(hash('sha256', $response->getContent()));
        $response->headers->set('Cache-Control', 'no-cache');
        $response->isNotModified($request);

        return $response;
    }
}
