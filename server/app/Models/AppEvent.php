<?php

namespace App\Models;

use App\Enums\AppEventType;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class AppEvent extends Model
{
    protected $table = 'app_events';

    protected $fillable = [
        'event_type',
        'app_id',
        'package_name',
        'version_name',
        'version_code',
        'ip_address',
        'device_model',
        'android_version',
        'abi',
        'client_version',
    ];

    protected function casts(): array
    {
        return [
            'event_type' => AppEventType::class,
            'version_code' => 'integer',
        ];
    }

    public function app(): BelongsTo
    {
        return $this->belongsTo(StoreApp::class, 'app_id');
    }
}
