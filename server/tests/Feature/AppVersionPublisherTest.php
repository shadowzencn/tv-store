<?php

namespace Tests\Feature;

use App\Enums\VersionStatus;
use App\Models\AppVersion;
use App\Models\StoreApp;
use App\Services\AppVersionPublisher;
use App\Services\VersionRuleException;
use Illuminate\Database\QueryException;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AppVersionPublisherTest extends TestCase
{
    use RefreshDatabase;

    private AppVersionPublisher $publisher;

    private StoreApp $storeApp;

    protected function setUp(): void
    {
        parent::setUp();
        $this->publisher = new AppVersionPublisher;
        $this->storeApp = StoreApp::factory()->create(['package_name' => 'com.example.demo']);
    }

    private function assertRejected(string $reason, callable $action): void
    {
        try {
            $action();
            $this->fail("Expected VersionRuleException with reason {$reason}");
        } catch (VersionRuleException $e) {
            $this->assertSame($reason, $e->reason);
        }
    }

    public function test_publishing_first_version_marks_it_published(): void
    {
        $version = AppVersion::factory()->for($this->storeApp, 'app')->create();

        $this->publisher->publish($version);

        $version->refresh();
        $this->assertSame(VersionStatus::Published, $version->status);
        $this->assertNotNull($version->published_at);
    }

    public function test_publishing_new_version_supersedes_the_old_one_and_keeps_it(): void
    {
        $v1 = AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 1]);
        $this->publisher->publish($v1);
        $v2 = AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 2]);

        $this->publisher->publish($v2);

        $this->assertSame(VersionStatus::Superseded, $v1->fresh()->status);
        $this->assertNotNull($v1->fresh()->published_at);
        $this->assertSame(VersionStatus::Published, $v2->fresh()->status);
        $this->assertSame(1, $this->storeApp->versions()->where('status', 'published')->count());
        $this->assertSame(2, $this->storeApp->versions()->count());
    }

    public function test_package_name_mismatch_is_rejected(): void
    {
        $version = AppVersion::factory()->for($this->storeApp, 'app')->make(['package_name' => 'com.other.app']);

        $this->assertRejected(VersionRuleException::PACKAGE_MISMATCH, fn () => $this->publisher->assertAcceptable($version));
    }

    public function test_signer_mismatch_with_previously_published_version_is_rejected(): void
    {
        $v1 = AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 1]);
        $this->publisher->publish($v1);
        $this->publisher->withdraw($v1);

        $other = AppVersion::factory()->for($this->storeApp, 'app')->create([
            'version_code' => 2,
            'signer_sha256' => str_repeat('b', 64),
        ]);

        $this->assertRejected(VersionRuleException::SIGNER_MISMATCH, fn () => $this->publisher->publish($other));
        $this->assertSame(VersionStatus::Draft, $other->fresh()->status);
    }

    public function test_signer_comparison_ignores_case(): void
    {
        $v1 = AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 1]);
        $this->publisher->publish($v1);
        $v2 = AppVersion::factory()->for($this->storeApp, 'app')->make([
            'version_code' => 2,
            'signer_sha256' => strtoupper($v1->signer_sha256),
        ]);

        $this->publisher->assertAcceptable($v2);
        $this->assertSame($v1->signer_sha256, $v2->signer_sha256);
    }

    public function test_drafts_with_different_signer_do_not_block_when_nothing_was_published(): void
    {
        AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 1, 'signer_sha256' => str_repeat('c', 64)]);
        $v2 = AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 2]);

        $this->publisher->publish($v2);

        $this->assertSame(VersionStatus::Published, $v2->fresh()->status);
    }

    public function test_lower_version_code_than_published_is_rejected(): void
    {
        $v5 = AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 5]);
        $this->publisher->publish($v5);
        $v4 = AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 4]);

        $this->assertRejected(VersionRuleException::VERSION_CODE_DOWNGRADE, fn () => $this->publisher->publish($v4));
        $this->assertSame(VersionStatus::Published, $v5->fresh()->status);
        $this->assertSame(VersionStatus::Draft, $v4->fresh()->status);
    }

    public function test_duplicate_version_code_is_rejected(): void
    {
        AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 3]);
        $duplicate = AppVersion::factory()->for($this->storeApp, 'app')->make(['version_code' => 3]);

        $this->assertRejected(VersionRuleException::DUPLICATE_VERSION_CODE, fn () => $this->publisher->assertAcceptable($duplicate));
    }

    public function test_withdraw_only_applies_to_published_versions(): void
    {
        $version = AppVersion::factory()->for($this->storeApp, 'app')->create();
        $this->assertRejected(VersionRuleException::INVALID_STATUS, fn () => $this->publisher->withdraw($version));

        $this->publisher->publish($version);
        $this->assertRejected(VersionRuleException::INVALID_STATUS, fn () => $this->publisher->publish($version));
        $this->publisher->withdraw($version);

        $this->assertSame(VersionStatus::Withdrawn, $version->fresh()->status);
        $this->assertNotNull($version->fresh()->published_at);
    }

    public function test_database_rejects_two_published_versions_for_one_app(): void
    {
        AppVersion::factory()->for($this->storeApp, 'app')->published()->create(['version_code' => 1]);

        $this->expectException(QueryException::class);
        AppVersion::factory()->for($this->storeApp, 'app')->published()->create(['version_code' => 2]);
    }
}
