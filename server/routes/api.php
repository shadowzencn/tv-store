<?php

use App\Http\Controllers\Api\AppEventController;
use App\Http\Controllers\Api\CatalogController;
use App\Http\Controllers\Api\ClientVersionController;
use App\Http\Controllers\Api\FeedbackController;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->group(function () {
    Route::get('catalog', CatalogController::class);
    Route::get('client/version', ClientVersionController::class);
    Route::post('feedback', [FeedbackController::class, 'store'])->middleware('throttle:feedback');
    Route::post('events', [AppEventController::class, 'store'])->middleware('throttle:events');
});
