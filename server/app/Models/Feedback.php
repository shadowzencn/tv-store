<?php

namespace App\Models;

use App\Enums\FeedbackStatus;
use App\Enums\FeedbackType;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Feedback extends Model
{
    protected $table = 'feedback';

    protected $fillable = [
        'type', 'app_id', 'description', 'device_model', 'android_version', 'client_version',
        'status', 'handling_note',
    ];

    protected $attributes = [
        'status' => 'pending',
    ];

    protected function casts(): array
    {
        return [
            'type' => FeedbackType::class,
            'status' => FeedbackStatus::class,
        ];
    }

    public function app(): BelongsTo
    {
        return $this->belongsTo(StoreApp::class, 'app_id');
    }
}
