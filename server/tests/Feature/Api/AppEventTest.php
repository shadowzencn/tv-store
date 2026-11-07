<?php

namespace Tests\Feature\Api;

use App\Enums\AppEventType;
use App\Models\AppEvent;
use App\Models\StoreApp;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AppEventTest extends TestCase
{
    use RefreshDatabase;

    private function payload(array $overrides = []): array
    {
        return array_merge([
            'type' => 'download',
            'appId' => 'smarttube',
            'packageName' => 'org.smarttube.stable',
            'versionName' => '32.68',
            'versionCode' => 2458,
            'deviceModel' => 'TCL Q10L',
            'androidVersion' => 'Android 11 (API 30)',
            'abi' => 'armeabi-v7a',
            'clientVersion' => '0.1.0 (1)',
        ], $overrides);
    }

    public function test_download_and_install_events_record_ip_and_device_model(): void
    {
        $app = StoreApp::factory()->create(['id' => 'smarttube', 'name' => 'SmartTube']);

        $res1 = $this->withServerVariables(['REMOTE_ADDR' => '223.5.5.5'])
            ->postJson('/api/v1/events', $this->payload(['type' => 'download']));

        $res1->assertCreated()->assertJsonStructure(['id']);
        $event = AppEvent::findOrFail($res1->json('id'));
        $this->assertSame(AppEventType::Download, $event->event_type);
        $this->assertSame($app->id, $event->app_id);
        $this->assertSame('223.5.5.5', $event->ip_address);
        $this->assertSame('TCL Q10L', $event->device_model);
        $this->assertSame('Android 11 (API 30)', $event->android_version);
        $this->assertSame('armeabi-v7a', $event->abi);

        $res2 = $this->withServerVariables(['REMOTE_ADDR' => '223.5.5.5'])
            ->postJson('/api/v1/events', $this->payload(['type' => 'install']));

        $res2->assertCreated();
        $this->assertSame(1, $app->downloadEvents()->count());
        $this->assertSame(1, $app->installEvents()->count());
    }

    public function test_unknown_app_id_still_stores_event_with_null_foreign_key(): void
    {
        $res = $this->postJson('/api/v1/events', $this->payload(['appId' => 'unknown-app']));
        $res->assertCreated();

        $event = AppEvent::findOrFail($res->json('id'));
        $this->assertNull($event->app_id);
        $this->assertSame('org.smarttube.stable', $event->package_name);
    }
}
