#!/system/bin/sh
# Reticle · 一键装/修复脚本（装 APK → 权限放行 → 恢复无障碍 → ping 自检）
# 用法：sh 装工具.sh [APK路径]
#   默认 APK = /sdcard/手机通道/通道三/工程/输出/reticle.apk（也可用 APK=<路径> 环境变量、或第 1 个参数指定）
# 24 号单（2026-10-07）口径：包名 app.reticle；运行目录 /sdcard/Reticle；MCP 端口 8770。
#   ★ 旧包 com.mcp.shell 已于 2026-10-07 卸掉（退路＝备份 APK＋Reticle 旧版本包）——本脚本只动 app.reticle。
# 场景：①首次安装 ②升级覆盖安装（保数据） ③无障碍被系统清掉后一键修复/自检
# 退出码：0=成功；2/3=失败（最后几行为原因）。
PKG=app.reticle
SVC="$PKG/$PKG.VeriService"
D=/sdcard/Reticle
APK="${1:-${APK:-/sdcard/手机通道/通道三/工程/输出/reticle.apk}}"
TMP=/data/local/tmp/reticle-install.apk

get_list() {
  n=0
  while [ $n -lt 6 ]; do
    v=$(settings get secure enabled_accessibility_services 2>&1)
    case "$v" in
      *Failure*|"") n=$((n+1)); sleep 1;;
      *) printf '%s\n' "$v"; return 0;;
    esac
  done
  printf ''
}

add_tokens() {
  for t in $1; do
    case "$t" in */*) ;; *) continue;; esac
    case ":$OUT:" in *":$t:"*) continue;; esac
    OUT="${OUT:+$OUT:}$t"
  done
}

merge() {
  OUT=""
  IFS=:
  add_tokens "$SVC"
  add_tokens "$1"
  add_tokens "$2"
  unset IFS
  printf '%s\n' "$OUT"
}

echo "== Reticle · 一键装/修复 =="
[ -f "$APK" ] || { echo "FAIL 找不到 APK：$APK"; exit 2; }
C0=$(get_list)
echo "[1/5] 安装：$(basename "$APK")"
cp "$APK" "$TMP" || { echo "FAIL 拷贝到临时目录失败"; exit 2; }
O=$(pm install -r "$TMP" 2>&1); rm -f "$TMP"
if ! echo "$O" | grep -q "Success"; then
  echo "FAIL 安装未成功：$O"
  echo "需要用户操作：若手机弹出「继续安装」类确认框，点一下后重跑本脚本。"
  exit 2
fi
echo "     OK（安装成功）"

echo "[2/5] 权限放行（vivo 实测：shell 设 ACCESS_RESTRICTED_SETTINGS／MANAGE_EXTERNAL_STORAGE 不落地——开关要用户在设置里亲手开；下面这几条留着以便别的机型）"
cmd appops set $PKG ACCESS_RESTRICTED_SETTINGS allow 2>&1 | head -1
cmd appops set $PKG MANAGE_EXTERNAL_STORAGE allow 2>&1 | head -1
pm grant $PKG android.permission.POST_NOTIFICATIONS 2>&1 | head -1
pm grant $PKG android.permission.WRITE_SECURE_SETTINGS 2>&1 | head -1
echo "     ACCESS_RESTRICTED_SETTINGS=$(cmd appops get $PKG ACCESS_RESTRICTED_SETTINGS 2>/dev/null | head -1)"
echo "     MANAGE_EXTERNAL_STORAGE=$(cmd appops get $PKG MANAGE_EXTERNAL_STORAGE 2>/dev/null | head -1)"

echo "[3/5] 无障碍开关（合并恢复＋读回验证）"
C=$(get_list)
TARGET=$(merge "$C0" "$C")
try=0; RB=""
while [ $try -lt 3 ]; do
  CUR=$(get_list)
  if [ "$CUR" != "$TARGET" ]; then settings put secure enabled_accessibility_services "$TARGET" >/dev/null 2>&1; fi
  settings put secure accessibility_enabled 1 >/dev/null 2>&1
  sleep 1
  RB=$(get_list)
  case "$RB" in *"$SVC"*) break;; esac
  try=$((try+1))
done
if [ "$RB" = "$TARGET" ]; then
  echo "     读回一致：$RB"
elif [ -n "$RB" ]; then
  echo "     读回（注意与目标不同）：$RB"
else
  echo "     读回失败（settings 服务无响应）"
fi

# ── 28.7 清理（2026-10-07）：旧包 com.mcp.shell 已卸 ──────────────
# ① 本 ROM（vivo）拦"命令行改设置"：上面那些 settings put 都不落地，读回会"与目标不同"。
# ② 覆盖安装（pm install -r）时系统会把 enabled_accessibility_services **整张清空**（本应用那条也一起没）→ 必须补回。
# ③ 能落地的写法：content insert（值里不含冒号）；本脚本只补 app.reticle 这一条。
CUR=$(get_list)
case "$CUR" in
  *"$SVC"*) ;;
  *)
    content insert --uri content://settings/secure --bind name:s:enabled_accessibility_services \
      --bind value:s:"$SVC" >/dev/null 2>&1
    echo "     本应用无障碍不在册 → 已用 content insert 写回（本 ROM 唯一能落地的写法）"
    ;;
esac
n=0
while [ $n -lt 12 ]; do
  case "$(get_list)" in *"$SVC"*) break;; esac
  sleep 2; n=$((n+1))
done
echo "     看门狗自补后读回：$(get_list)"
echo "[4/5] 运行目录与自检：ping（最多等约 40 秒）"
mkdir -p "$D/in" "$D/out" 2>/dev/null
OK=; R=""
i=0
while [ $i -lt 12 ]; do
  S="p$(date +%s)$i"
  { echo "cmd=ping"; echo "seq=$S"; echo "z=1"; } > "$D/in/slot_6.cmd"
  j=0
  while [ $j -lt 3 ]; do
    if [ -f "$D/out/slot_6-$S.json" ]; then R=$(cat "$D/out/slot_6-$S.json"); break; fi
    sleep 1; j=$((j+1))
  done
  if echo "$R" | grep -q '"ok":1'; then OK=1; break; fi
  i=$((i+1))
done
echo "     $R"

echo "[5/5] 结论"
if [ -n "$OK" ]; then
  echo "✅ 完成：服务在线；$R"
  mkdir -p /sdcard/Reticle/logs 2>/dev/null
  { echo "时间 $(date '+%Y-%m-%d %H:%M:%S')"; echo "结果 完成：服务在线"; echo "ping $R"; echo "无障碍 $(get_list)"; } > /sdcard/Reticle/logs/install-last.txt
  exit 0
fi
echo "❌ 未通过：ping 无回应。"
mkdir -p /sdcard/Reticle/logs 2>/dev/null
{ echo "时间 $(date '+%Y-%m-%d %H:%M:%S')"; echo "结果 未通过：ping 无回应"; echo "无障碍 $(get_list)"; } > /sdcard/Reticle/logs/install-last.txt
echo "需要用户操作：到 设置→快捷与辅助→无障碍 里把「Reticle」开关打开，然后重跑本脚本。"
exit 3
