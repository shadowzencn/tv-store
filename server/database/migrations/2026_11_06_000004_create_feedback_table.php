<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('feedback', function (Blueprint $table) {
            $table->id();
            $table->string('type', 30);
            $table->string('app_id', 64)->nullable();
            $table->foreign('app_id')->references('id')->on('apps')->nullOnDelete();
            $table->text('description')->nullable();
            $table->string('device_model', 100);
            $table->string('android_version', 20);
            $table->string('client_version', 50);
            $table->string('status', 20)->default('pending');
            $table->text('handling_note')->nullable();
            $table->timestamps();

            $table->index(['status', 'created_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('feedback');
    }
};
