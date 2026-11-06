<?php

namespace App\Models;

use App\Enums\VersionStatus;
use Database\Factories\StoreAppFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\Relations\HasOne;

/**
 * An application listed in the store. Named StoreApp to avoid clashing with the App facade alias.
 */
class StoreApp extends Model
{
    /** @use HasFactory<StoreAppFactory> */
    use HasFactory;

    protected $table = 'apps';

    public $incrementing = false;

    protected $keyType = 'string';

    protected $fillable = [
        'id', 'package_name', 'name', 'summary', 'description', 'category_id',
        'is_featured', 'is_listed', 'sort_order', 'icon_path',
    ];

    protected function casts(): array
    {
        return [
            'is_featured' => 'boolean',
            'is_listed' => 'boolean',
            'sort_order' => 'integer',
        ];
    }

    public function category(): BelongsTo
    {
        return $this->belongsTo(Category::class);
    }

    public function versions(): HasMany
    {
        return $this->hasMany(AppVersion::class, 'app_id');
    }

    public function publishedVersion(): HasOne
    {
        return $this->hasOne(AppVersion::class, 'app_id')->where('status', VersionStatus::Published->value);
    }
}
