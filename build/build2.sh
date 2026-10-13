#!/system/bin/sh
# 24 号单 · 设备侧构建脚本 v2（不需要 Termux；用 shell 身份跑）
# 39 号单 · 加 ID 参数：ID=B 出「副实例」包（包名 app.reticle.b / MCP 8771 / 目录 /sdcard/ReticleB）
# 用法：sh /sdcard/手机通道/通道三/工程/工具链/build2.sh         → 主包 输出/reticle.apk
#       ID=B sh /sdcard/手机通道/通道三/工程/工具链/build2.sh    → 副包 输出/reticle-b.apk
#   身份替换只在临时工作区做，正本源码一个字节都不动。
# 产物：通道三/工程/输出/*.apk（release 签名；钥匙与口令见 备份/Reticle-签名/）
set -e
SB="${SB:-/sdcard/手机通道/通道三/工程}"   # 源码树（可用环境变量指向自己的路径）
TC="${TC:-$SB/工具链}"                # 构建工具链（仓库不含，需自备）
ID="${ID:-}"
if [ "$ID" = "B" ]; then
  W=/data/local/tmp/reticle-build-b
  PKG=app.reticle.b
  PORT=8771
  RDIR=/sdcard/ReticleB
  OUTNAME=reticle-b.apk
else
  W=/data/local/tmp/reticle-build
  PKG=app.reticle
  PORT=8770
  RDIR=/sdcard/Reticle
  OUTNAME=reticle.apk
fi
JAR=$TC/lib/android.jar
KEEP="${KEEP:-$SB/物料}"             # 第三方依赖（仓库不含，需自备）
SRCDIR=$SB/src
RESDIR=$SB/res
MANIFEST=$SB/AndroidManifest.xml

# 29 号单：液态玻璃库（QWEA0/Liquid-Glass-Android v2.0.11）需要 kotlin-stdlib 2.0.21 / core-ktx 1.13.1；
#   物料里旧的同名库（1.7.10／1.8.0）会造成重复类（d8 报 defined multiple times）→ 显式排除，由新版本顶替。
EXCL=" kotlin-stdlib-1.7.10.jar kotlin-stdlib-common-1.7.10.jar kotlin-stdlib-jdk7-1.6.0.jar kotlin-stdlib-jdk8-1.6.0.jar core-ktx-1.8.0.jar "
skip_jar() { case "$EXCL" in *" $1 "*) return 0 ;; esac; return 1; }
APP=/system/bin/app_process
ECJ="org.eclipse.jdt.internal.compiler.batch.Main"
D8M="com.android.tools.r8.D8"
SIGN="com.android.apksigner.ApkSignerTool"

echo "== 0) 准备 $(date '+%H:%M:%S') ==  身份=$ID 包名=$PKG 端口=$PORT 目录=$RDIR 产物=$OUTNAME"
rm -rf $W; mkdir -p $W/bin $W/classes $W/dex $W/gen $W/reszip $W/out
cp $TC/bin/* $W/bin/ 2>/dev/null || true
chmod 755 $W/bin/aapt2
LL=$W/bin
# 38 号单：出包前环境自检（缺件就点名，别再让后面静默失败）
MISS=""
[ -f $TC/bin/aapt2 ] || MISS="$MISS 工具链/bin/aapt2"
[ -f $JAR ] || MISS="$MISS 工具链/lib/android.jar"
for d in ecj d8 d8-2 apksigner buildtool; do [ -f $TC/dex/$d.dex ] || MISS="$MISS 工具链/dex/$d.dex"; done
for j in core-1.9.0.jar appcompat-1.6.1.jar material-1.9.0.jar liquidglass-2.0.11.jar vision-common-17.3.0.jar play-services-tasks-18.2.0.jar kotlin-stdlib-2.0.21.jar; do [ -f "$KEEP/classes/$j" ] || MISS="$MISS 物料/classes/$j"; done
if [ -n "$MISS" ]; then
  echo "！缺件（补齐再出包）："
  for m in $MISS; do echo "   · $m"; done
  echo "   （可从 备份/24-改前-2026-10-07/工程/ 原样恢复）"
  exit 4
fi
echo "   环境自检：齐 ✓"

# ── 39 号单：身份参数化（ID=B 时；只在临时工作区替换，正本不动）──
if [ "$ID" = "B" ]; then
  echo "== 0b) 身份参数化（临时副本）=="
  mkdir -p $W/src; cp $SB/src/*.java $W/src/
  cp -r $SB/res $W/res
  cp $SB/AndroidManifest.xml $W/AndroidManifest.xml
  SRCDIR=$W/src
  RESDIR=$W/res
  MANIFEST=$W/AndroidManifest.xml
  # manifest + 资源里的包名（authority／组件全名自然跟着变）
  for f in $W/AndroidManifest.xml $W/res/values/strings.xml $W/res/xml/accessibility_service_config.xml; do
    [ -f "$f" ] || continue
    sed -i "s|app\.reticle|$PKG|g" "$f"
  done
  # 显示名：系统无障碍列表 / 通知里能一眼分辨主副（人话命名，不改身份常量）
  sed -i 's|>Reticle<|>Reticle B<|' $W/res/values/strings.xml
  sed -i 's|>Reticle（|>Reticle B（|' $W/res/values/strings.xml
  # 源码：目录 → 包名 → 端口 → 通知编号
  for f in $(find $W/src -name '*.java'); do
    sed -i "s|/sdcard/Reticle|$RDIR|g" "$f"
    sed -i "s|app\.reticle|$PKG|g" "$f"
    sed -i "s|8770|$PORT|g" "$f"
    sed -i "s|工具链/build2.sh|工具链/build2b.sh|g" "$f"
    sed -i "s|工具/装工具.sh|工具/装工具B.sh|g" "$f"
    sed -i "s|public static final int DEF_ID = 1;|public static final int DEF_ID = 101;|" "$f"
    sed -i "s|public static final int NID = 2;|public static final int NID = 102;|" "$f"
  done
  # 自检：B 包里不许再出现主包身份
  R1=$(grep -rEn "app\.reticle;" $W/src $W/AndroidManifest.xml | wc -l)
  R2=$(grep -rEn "8770" $W/src $W/AndroidManifest.xml | wc -l)
  R3=$(grep -rEn "/sdcard/Reticle($|[^B])" $W/src | wc -l)
  echo "   残留检查：旧包名 $R1 处｜旧端口 $R2 处｜旧目录 $R3 处（应全 0）"
  if [ "$R1" != "0" ] || [ "$R2" != "0" ] || [ "$R3" != "0" ]; then
    echo "！参数化自检不过："
    grep -rEn "app\.reticle;|8770|/sdcard/Reticle($|[^B])" $W/src $W/AndroidManifest.xml | head -20
    exit 8
  fi
  echo "   身份自检：过 ✓（包名 $(grep -m1 'package=' $W/AndroidManifest.xml | sed 's/.*package="//; s/".*//')）"
fi

echo "== 1) aapt2 compile =="
LD_LIBRARY_PATH=$LL $LL/aapt2 compile --dir "$RESDIR" -o $W/reszip/app.zip
RESARGS="-R $W/reszip/app.zip"
for z in "$KEEP"/res-zips/*.zip; do [ -e "$z" ] || continue; RESARGS="$RESARGS -R $z"; done
AASSET=""; [ -d "$KEEP/assets" ] && AASSET="-A $KEEP/assets"

# 26 号单：为第三方库生成 R 类（material/appcompat 等运行时要引用它们自己的 R；缺了会 NoClassDefFoundError）
PKGS=$(grep -h -o 'package="[^"]*"' "$KEEP"/manifest碎片/*.xml 2>/dev/null | sed 's/package="//; s/"//' | sort -u | tr '\n' ':' | sed 's/:$//')
EXTRA=""; [ -n "$PKGS" ] && EXTRA="--extra-packages $PKGS"
echo "     库包名 $(echo "$PKGS" | tr ':' '\n' | grep -c .) 个 → 生成 R 类"
echo "== 2) aapt2 link =="
LD_LIBRARY_PATH=$LL $LL/aapt2 link -o $W/base.apk --manifest "$MANIFEST" -I "$JAR" \
  --min-sdk-version 30 --target-sdk-version 35 --java $W/gen $EXTRA $RESARGS $AASSET --auto-add-overlay > $W/aapt2link.log 2>&1 || { echo "！aapt2 link 失败（末尾 30 行）："; tail -n 30 $W/aapt2link.log; exit 5; }
ls -l $W/base.apk

echo "== 3) ECJ 编译 =="
CP="$JAR"
for j in "$KEEP"/classes/*.jar; do [ -e "$j" ] || continue; b=$(basename "$j"); skip_jar "$b" && continue; CP="$CP:$j"; done
SRCS=""
for f in $(find "$SRCDIR" -name '*.java'); do SRCS="$SRCS $f"; done
CLASSPATH=$TC/dex/ecj.dex:$TC/ecj-res $APP /system/bin $ECJ \
  -source 1.8 -target 1.8 -nowarn -proc:none -d $W/classes -classpath "$CP" $SRCS $(find $W/gen -name "*.java") > $W/ecj.log 2>&1 || { echo "！ECJ 编译失败（末尾 30 行）："; tail -n 30 $W/ecj.log; exit 7; }
echo "javac(ECJ) ok: $(find $W/classes -name '*.class' | wc -l) 个 class"

echo "== 4) d8 =="
D8IN=""
for f in $(find $W/classes -name '*.class'); do D8IN="$D8IN $f"; done
for j in "$KEEP"/classes/*.jar; do [ -e "$j" ] || continue; b=$(basename "$j"); skip_jar "$b" && continue; D8IN="$D8IN $j"; done
CLASSPATH=$TC/dex/d8.dex:$TC/dex/d8-2.dex $APP -Xmx1536m /system/bin $D8M \
  --min-api 30 --lib "$JAR" --output $W/dex $D8IN > $W/d8.log 2>&1 || { echo "！d8 失败（末尾 40 行）："; tail -n 40 $W/d8.log; exit 6; }
ls -l $W/dex

echo "== 5) 组装 =="
DEXCSV=""
for f in $W/dex/classes*.dex; do DEXCSV="$DEXCSV,$f"; done
DEXCSV=${DEXCSV#,}
JNI="-"; [ -d "$KEEP/jni" ] && JNI="$KEEP/jni"
CLASSPATH=$TC/dex/buildtool.dex $APP /system/bin BuildTool $W/base.apk $W/unsigned.apk "$DEXCSV" "$JNI"
ls -l $W/unsigned.apk

echo "== 6) 签名（release）=="
# ---- 签名可配（57 号单）：环境变量优先；本仓库不含钥匙与口令 ----
KS="${KS:-./keys/release.p12}"    # 钥匙库（自行放置；仓库不提供）
KD="${KD:-./keys/口令.txt}"       # 口令文件（一行：口令=xxx）
KA="${KA:-release}"             # 钥匙别名
KP="${KP:-$(grep -m1 '^口令=' "$KD" 2>/dev/null | cut -d= -f2-)}"
if [ -n "$KP" ] && [ -f "$KS" ]; then
  echo "    用 release 钥匙：$KS"
  CLASSPATH=$TC/dex/apksigner.dex $APP /system/bin $SIGN sign \
    --ks "$KS" --ks-type PKCS12 --ks-key-alias "$KA" \
    --ks-pass "pass:$KP" --key-pass "pass:$KP" \
    --out $W/signed.apk $W/unsigned.apk
else
  echo "    ！找不到 release 钥匙或口令 → 回退 debug 签名（正式发布别这样）"
  CLASSPATH=$TC/dex/apksigner.dex $APP /system/bin $SIGN sign \
    --ks "$TC/debug.p12" --ks-type PKCS12 --ks-pass pass:android --key-pass pass:android \
    --out $W/signed.apk $W/unsigned.apk
fi
ls -l $W/signed.apk

echo "== 6b) 自证：apksigner verify =="
CLASSPATH=$TC/dex/apksigner.dex $APP /system/bin $SIGN verify --print-certs $W/signed.apk

echo "== 7) 产物 =="
mkdir -p "$SB/输出"; cp $W/signed.apk "$SB/输出/$OUTNAME"
ls -l "$SB/输出/$OUTNAME"; sha256sum "$SB/输出/$OUTNAME"
echo "== 完 $(date '+%H:%M:%S') =="
