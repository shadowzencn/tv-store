<?php

namespace Tests\Feature\Admin;

use App\Models\AppVersion;
use App\Models\Feedback;

class AdminPagesTest extends AdminTestCase
{
    public function test_every_admin_page_renders_in_chinese(): void
    {
        $version = AppVersion::factory()->create();
        Feedback::query()->create([
            'type' => 'install_failed', 'app_id' => $version->app_id, 'device_model' => 'TCL Q10L',
            'android_version' => '11', 'client_version' => '0.1.0',
        ]);

        $this->get('/admin')->assertOk()->assertSee('分类')->assertSee('应用')->assertSee('版本')->assertSee('反馈');
        $this->get('/admin/categories')->assertOk();
        $this->get('/admin/apps')->assertOk()->assertSee('当前发布版本');
        $this->get('/admin/apps/create')->assertOk();
        $this->get("/admin/apps/{$version->app_id}/edit")->assertOk()->assertSee('上传新版本');
        $this->get('/admin/versions')->assertOk();
        $this->get('/admin/versions/create')->assertOk()->assertSee('APK 文件');
        $this->get("/admin/versions/{$version->id}/edit")->assertOk()->assertSee($version->sha256);
        $this->get('/admin/feedback')->assertOk()->assertSee('TCL Q10L');
    }
}
