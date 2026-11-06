<?php

namespace Database\Factories;

use App\Models\Category;
use App\Models\StoreApp;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<StoreApp>
 */
class StoreAppFactory extends Factory
{
    protected $model = StoreApp::class;

    public function definition(): array
    {
        $slug = fake()->unique()->slug(2);

        return [
            'id' => $slug,
            'package_name' => 'com.example.'.str_replace('-', '', $slug),
            'name' => fake()->words(2, true),
            'summary' => fake()->sentence(),
            'description' => fake()->paragraph(),
            'category_id' => Category::factory(),
            'is_featured' => false,
            'is_listed' => true,
            'sort_order' => 0,
            'icon_path' => null,
        ];
    }
}
