<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('app_events', function (Blueprint $table) {
            $table->id();
            $table->string('event_type', 20);
            $table->string('app_id', 64)->nullable();
            $table->foreign('app_id')->references('id')->on('apps')->nullOnDelete();
            $table->string('package_name', 150)->nullable();
            $table->string('version_name', 50)->nullable();
            $table->unsignedBigInteger('version_code')->nullable();
            $table->string('ip_address', 45)->nullable();
            $table->string('device_model', 100);
            $table->string('android_version', 30);
            $table->string('abi', 50)->nullable();
            $table->string('client_version', 50);
            $table->timestamps();

            $table->index(['event_type', 'created_at']);
            $table->index(['app_id', 'event_type']);
            $table->index('ip_address');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('app_events');
    }
};
