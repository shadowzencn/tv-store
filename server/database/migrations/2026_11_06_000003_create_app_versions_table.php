<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('app_versions', function (Blueprint $table) {
            $table->id();
            $table->string('app_id', 64);
            $table->foreign('app_id')->references('id')->on('apps')->restrictOnDelete();
            $table->string('package_name');
            $table->string('version_name', 100);
            $table->unsignedBigInteger('version_code');
            $table->unsignedSmallInteger('min_sdk');
            $table->json('abis'); // empty array = no native libraries
            $table->unsignedBigInteger('size_bytes');
            $table->char('sha256', 64)->unique();
            $table->char('signer_sha256', 64);
            $table->string('file_path');
            $table->string('compatibility', 20)->default('unverified');
            $table->text('test_device_notes')->nullable();
            $table->text('known_issues')->nullable();
            $table->text('release_notes')->nullable();
            $table->string('status', 20)->default('draft');
            $table->timestamp('published_at')->nullable();
            $table->timestamps();

            $table->unique(['app_id', 'version_code']);
            $table->index(['status', 'published_at']);
        });

        // MySQL has no partial indexes; there the rule is enforced only by AppVersionPublisher.
        if (in_array(DB::getDriverName(), ['sqlite', 'pgsql'], true)) {
            DB::statement("CREATE UNIQUE INDEX app_versions_one_published ON app_versions (app_id) WHERE status = 'published'");
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('app_versions');
    }
};
