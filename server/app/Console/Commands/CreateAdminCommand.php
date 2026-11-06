<?php

namespace App\Console\Commands;

use App\Models\User;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Validator;

class CreateAdminCommand extends Command
{
    protected $signature = 'tvstore:create-admin
        {email : 管理员登录邮箱}
        {--name= : 显示名称，默认取邮箱 @ 前的部分}';

    protected $description = '创建后台管理员账号（密码从环境变量 TVSTORE_ADMIN_PASSWORD 读取，未设置时交互输入）';

    public const MIN_PASSWORD_LENGTH = 12;

    public function handle(): int
    {
        $email = strtolower(trim((string) $this->argument('email')));
        $name = trim((string) ($this->option('name') ?? '')) ?: strstr($email, '@', true);

        $validator = Validator::make(['email' => $email, 'name' => $name], [
            'email' => ['required', 'email', 'max:255'],
            'name' => ['required', 'string', 'max:255'],
        ]);
        if ($validator->fails()) {
            $this->error($validator->errors()->first());

            return self::FAILURE;
        }

        if (User::query()->where('email', $email)->exists()) {
            $this->error("邮箱 {$email} 已经存在管理员账号。");

            return self::FAILURE;
        }

        $password = $this->readPassword();
        if ($password === null) {
            return self::FAILURE;
        }

        $user = User::query()->create(['name' => $name, 'email' => $email, 'password' => $password]);
        $this->info("已创建管理员 {$user->email}（id {$user->id}）。");

        return self::SUCCESS;
    }

    private function readPassword(): ?string
    {
        $password = getenv('TVSTORE_ADMIN_PASSWORD');

        if ($password === false || $password === '') {
            if (! $this->input->isInteractive()) {
                $this->error('非交互模式下请通过环境变量 TVSTORE_ADMIN_PASSWORD 提供密码。');

                return null;
            }

            $password = (string) $this->secret('密码（至少 '.self::MIN_PASSWORD_LENGTH.' 个字符）');
            if ($password !== (string) $this->secret('再次输入密码')) {
                $this->error('两次输入的密码不一致。');

                return null;
            }
        }

        if (mb_strlen($password) < self::MIN_PASSWORD_LENGTH) {
            $this->error('密码至少需要 '.self::MIN_PASSWORD_LENGTH.' 个字符。');

            return null;
        }

        return $password;
    }
}
