<?php

namespace App\Http\Controllers\Api;

use App\Enums\AppEventType;
use App\Http\Controllers\Controller;
use App\Models\AppEvent;
use App\Models\StoreApp;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;

class AppEventController extends Controller
{
    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'type' => ['required', Rule::enum(AppEventType::class)],
            'appId' => ['nullable', 'string', 'max:64'],
            'packageName' => ['nullable', 'string', 'max:150'],
            'versionName' => ['nullable', 'string', 'max:50'],
            'versionCode' => ['nullable', 'integer', 'min:0'],
            'deviceModel' => ['required', 'string', 'max:100'],
            'androidVersion' => ['required', 'string', 'max:30'],
            'abi' => ['nullable', 'string', 'max:50'],
            'clientVersion' => ['required', 'string', 'max:50'],
        ]);

        $appId = $data['appId'] ?? null;
        if ($appId !== null && ! StoreApp::query()->whereKey($appId)->exists()) {
            $appId = null;
        }

        $event = AppEvent::create([
            'event_type' => $data['type'],
            'app_id' => $appId,
            'package_name' => $data['packageName'] ?? null,
            'version_name' => $data['versionName'] ?? null,
            'version_code' => $data['versionCode'] ?? null,
            'ip_address' => $request->ip(),
            'device_model' => $data['deviceModel'],
            'android_version' => $data['androidVersion'],
            'abi' => $data['abi'] ?? null,
            'client_version' => $data['clientVersion'],
        ]);

        return response()->json(['id' => $event->id], 201);
    }
}
