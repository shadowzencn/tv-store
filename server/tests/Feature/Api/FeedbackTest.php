<?php

namespace Tests\Feature\Api;

use App\Enums\FeedbackStatus;
use App\Enums\FeedbackType;
use App\Models\Feedback;
use App\Models\StoreApp;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class FeedbackTest extends TestCase
{
    use RefreshDatabase;

    private function payload(array $overrides = []): array
    {
        return array_merge([
            'type' => 'install_failed',
            'description' => '点击安装后没有反应',
            'deviceModel' => 'TCL Q10L',
            'androidVersion' => '11',
            'clientVersion' => '0.1.0',
        ], $overrides);
    }

    public function test_valid_feedback_is_stored(): void
    {
        $app = StoreApp::factory()->create(['id' => 'demo']);

        $response = $this->postJson('/api/v1/feedback', $this->payload(['appId' => 'demo']));

        $response->assertCreated()->assertJsonStructure(['id']);
        $feedback = Feedback::findOrFail($response->json('id'));
        $this->assertSame(FeedbackType::InstallFailed, $feedback->type);
        $this->assertSame($app->id, $feedback->app_id);
        $this->assertSame('TCL Q10L', $feedback->device_model);
        $this->assertSame(FeedbackStatus::Pending, $feedback->status);
    }

    public function test_app_and_description_are_optional(): void
    {
        $this->postJson('/api/v1/feedback', $this->payload(['description' => null]))->assertCreated();
        $this->assertDatabaseHas('feedback', ['app_id' => null, 'description' => null]);
    }

    public function test_invalid_fields_return_422_error_format(): void
    {
        // Rejected requests also count against the limit.
        config(['tvstore.feedback_rate_limit' => ['per_minute' => 100, 'per_day' => 100]]);

        $cases = [
            ['type' => 'nonsense'],
            ['type' => null],
            ['appId' => 'does-not-exist'],
            ['deviceModel' => null],
            ['androidVersion' => ''],
            ['clientVersion' => null],
            ['description' => str_repeat('a', 2001)],
        ];

        foreach ($cases as $override) {
            $this->postJson('/api/v1/feedback', $this->payload($override))
                ->assertStatus(422)
                ->assertJsonPath('error.code', 'validation_failed')
                ->assertJsonStructure(['error' => ['code', 'message']]);
        }

        $this->assertDatabaseCount('feedback', 0);
    }

    public function test_feedback_is_rate_limited_per_ip(): void
    {
        config(['tvstore.feedback_rate_limit' => ['per_minute' => 2, 'per_day' => 100]]);

        $this->postJson('/api/v1/feedback', $this->payload())->assertCreated();
        $this->postJson('/api/v1/feedback', $this->payload())->assertCreated();

        $this->postJson('/api/v1/feedback', $this->payload())
            ->assertStatus(429)
            ->assertHeader('Retry-After')
            ->assertJsonPath('error.code', 'too_many_requests');

        $this->withServerVariables(['REMOTE_ADDR' => '10.0.0.2'])
            ->postJson('/api/v1/feedback', $this->payload())
            ->assertCreated();

        $this->assertDatabaseCount('feedback', 3);
    }
}
