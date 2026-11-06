<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('apps', function (Blueprint $table) {
            $table->string('id', 64)->primary(); // slug, stable identifier used by the TV client
            $table->string('package_name')->unique();
            $table->string('name', 100);
            $table->string('summary', 200)->nullable();
            $table->text('description')->nullable();
            $table->string('category_id', 64);
            $table->foreign('category_id')->references('id')->on('categories')->restrictOnDelete();
            $table->boolean('is_featured')->default(false);
            $table->boolean('is_listed')->default(false);
            $table->integer('sort_order')->default(0);
            $table->string('icon_path')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('apps');
    }
};
