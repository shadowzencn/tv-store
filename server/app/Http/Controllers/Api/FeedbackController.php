<?php

namespace App\Http\Controllers\Api;

use App\Enums\FeedbackType;
use App\Http\Controllers\Controller;
use App\Models\Feedback;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;

class FeedbackController extends Controller
{
    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'type' => ['required', Rule::enum(FeedbackType::class)],
            'appId' => ['nullable', 'string', 'max:64', 'exists:apps,id'],
            'description' => ['nullable', 'string', 'max:2000'],
            'deviceModel' => ['required', 'string', 'max:100'],
            'androidVersion' => ['required', 'string', 'max:20'],
            'clientVersion' => ['required', 'string', 'max:50'],
        ]);

        $feedback = Feedback::create([
            'type' => $data['type'],
            'app_id' => $data['appId'] ?? null,
            'description' => $data['description'] ?? null,
            'device_model' => $data['deviceModel'],
            'android_version' => $data['androidVersion'],
            'client_version' => $data['clientVersion'],
        ]);

        return response()->json(['id' => $feedback->id], 201);
    }
}
