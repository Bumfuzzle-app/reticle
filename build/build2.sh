#!/system/bin/sh
# 24 号单 · 设备侧构建脚本 v2（不需要 Termux；用 shell 身份跑）
# 39 号单 · 加 ID 参数：ID=B 出「副实例」包（包名 app.reticle.b / MCP 8771 / 目录 /sdcard/ReticleB）
# 78 号单（2026-10-17）· 增量编译 + 手动 jar/AAR
#   用法（家版）：sh /sdcard/workbench/构建/build2.sh        → 输出/reticle.apk（增量）
#                 ID=B sh /sdcard/workbench/构建/build2.sh   → 输出/reticle-b.apk
#                 NOINCR=1 sh ...                            → 强制全量（对照/排错）
#   增量怎么判（缓存目录 $W-cache，与每次清空的 $W 分开）：
#     · 资源清单（路径+大小+改动时刻）没变 ⇒ 跳 aapt2 compile
#     · 资源+manifest+依赖包名+assets 没变 ⇒ 跳 aapt2 link（复用 base.apk 与 R 类）
#     · java/gen 清单没变 ⇒ 跳 ECJ；变了 ⇒ 只重编「变化文件所在包」（包级增量）
#     · 依赖 jar 清单没变 ⇒ 跳依赖部分 d8；工程 class 清单没变 ⇒ 跳工程部分 d8
#     · 改 java ⇒ 该包重编；改 manifest/包名/依赖 ⇒ link 缓存键变，不会误用旧缓存
#   手动依赖：工程 libs/*.jar → classpath 与 dex；libs/*.aar → classes.jar + res + assets + jni（仍不给 Gradle）
# 产物：/sdcard/workbench/构建/输出/*.apk（release 签名；钥匙与口令见旧家 备份/Reticle-签名/）
set -e
SB="${SB:-/sdcard/手机通道/通道三/工程}"   # 源码树（环境变量优先；仓库 BUILD.md 有说明）
TC="${TC:-/sdcard/workbench/构建/工具链}"  # 构建工具链（仓库不含，需自备）
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
CACHE="${CACHE:-$W-cache}"                # 增量缓存（独立于 $W；清了＝下次自动全量）
JAR=$TC/lib/android.jar
KEEP="${KEEP:-/sdcard/workbench/构建/物料}"
SRCDIR=$SB/src
RESDIR=$SB/res
MANIFEST=$SB/AndroidManifest.xml
LIBS=$SB/libs
NOINCR="${NOINCR:-0}"

# 29 号单：液态玻璃库要 kotlin-stdlib 2.0.21 / core-ktx 1.13.1，旧同名库会造成重复类 ⇒ 显式排除
EXCL=" kotlin-stdlib-1.7.10.jar kotlin-stdlib-common-1.7.10.jar kotlin-stdlib-jdk7-1.6.0.jar kotlin-stdlib-jdk8-1.6.0.jar core-ktx-1.8.0.jar "
skip_jar() { case "$EXCL" in *" $1 "*) return 0 ;; esac; return 1; }
APP=/system/bin/app_process
ECJ="org.eclipse.jdt.internal.compiler.batch.Main"
D8M="com.android.tools.r8.D8"
SIGN="com.android.apksigner.ApkSignerTool"

T0=$(date +%s)
step() { echo "== $* @$(date '+%H:%M:%S')（累计 $(( $(date +%s) - T0 ))s）"; }
SKIP=""
skipped() { SKIP="$SKIP $1"; }

# 指纹：路径 + 大小 + 改动时刻（秒）三元组；目录递归、文件本体。改了内容/换名/增删都会变。
fp() {
  # 78：内容级指纹（sha256）——比 mtime 准：mtime 变了内容没变不算变、保留 mtime 覆盖内容也会被认出来
  for p in "$@"; do
    [ -e "$p" ] || continue
    if [ -d "$p" ]; then find "$p" -type f -exec sha256sum {} + 2>/dev/null
    else find "$p" -maxdepth 1 -exec sha256sum {} + 2>/dev/null; fi
  done | sort | sha256sum | cut -c1-16
}

step "0) 准备"
echo "   身份=$ID 包名=$PKG 端口=$PORT 目录=$RDIR 产物=$OUTNAME"
[ "$NOINCR" = "1" ] && echo "   NOINCR=1：本次强制全量（不读缓存）"
MISS=""
[ -f $TC/bin/aapt2 ] || MISS="$MISS 工具链/bin/aapt2（Termux可装；连同目录lib*.so）"
[ -f $JAR ] || MISS="$MISS 工具链/lib/android.jar（SDK-platform-35）"
for d in ecj d8 d8-2 apksigner buildtool; do [ -f $TC/dex/$d.dex ] || MISS="$MISS 工具链/dex/$d.dex"; done
[ -d $TC/ecj-res ] || MISS="$MISS 工具链/ecj-res（ECJ资源目录）"
for j in core-1.9.0.jar appcompat-1.6.1.jar material-1.9.0.jar liquidglass-2.0.11.jar vision-common-17.3.0.jar play-services-tasks-18.2.0.jar kotlin-stdlib-2.0.21.jar; do [ -f "$KEEP/classes/$j" ] || MISS="$MISS 物料/classes/$j"; done
[ -f $MANIFEST ] || MISS="$MISS $MANIFEST（改SB=指到你的源码树）"
[ -d $SRCDIR ] || MISS="$MISS $SRCDIR（改SB=指到你的源码树）"
[ -d $RESDIR ] || MISS="$MISS $RESDIR（改SB=指到你的源码树）"
if [ -n "$MISS" ]; then
  echo "！缺件（补齐再出包）："
  for m in $MISS; do echo "   · $m"; done
  echo "   ——要什么／从哪拿／路径怎么配：见仓库 BUILD.md「路径与物料」一节；应用内可 build action=check 复检。"
  exit 4
fi
echo "   环境自检：齐 ✓"
mkdir -p "$CACHE"
rm -rf "$W"
mkdir -p "$W/classes" "$W/dex" "$W/dexapp" "$W/gen" "$W/reszip"

# 工具链 bin（12MB）不必每次从 /sdcard 复制：缓存到 $CACHE/bin
BINK=$(fp "$TC/bin")
if [ "$NOINCR" != "1" ] && [ -x "$CACHE/bin/aapt2" ] && [ "$(cat "$CACHE/bin/.key" 2>/dev/null)" = "$BINK" ]; then
  echo "   工具链 bin：用缓存（$CACHE/bin）"
else
  rm -rf "$CACHE/bin"; mkdir -p "$CACHE/bin"
  cp "$TC/bin"/* "$CACHE/bin/" 2>/dev/null || true
  chmod 755 "$CACHE/bin/aapt2"
  echo "$BINK" > "$CACHE/bin/.key"
  echo "   工具链 bin：从 $TC/bin 复制到缓存"
fi
LL="$CACHE/bin"

# ── 39 号单：身份参数化（只在临时工作区替换，正本源码一个字节都不动）──
if [ "$ID" = "B" ]; then
  step "0b) 身份参数化（临时副本）"
  mkdir -p $W/src; cp -p $SB/src/*.java $W/src/
  cp -rp $SB/res $W/res
  cp -p $SB/AndroidManifest.xml $W/AndroidManifest.xml
  SRCDIR=$W/src
  RESDIR=$W/res
  MANIFEST=$W/AndroidManifest.xml
  for f in $W/AndroidManifest.xml $W/res/values/strings.xml $W/res/xml/accessibility_service_config.xml; do
    [ -f "$f" ] || continue
    sed -i "s|app\.reticle|$PKG|g" "$f"
  done
  sed -i 's|>Reticle<|>Reticle B<|' $W/res/values/strings.xml
  sed -i 's|>Reticle（|>Reticle B（|' $W/res/values/strings.xml
  for f in $(find $W/src -name '*.java'); do
    sed -i "s|/sdcard/Reticle|$RDIR|g" "$f"
    sed -i "s|app\.reticle|$PKG|g" "$f"
    sed -i "s|8770|$PORT|g" "$f"
    sed -i "s|工具链/build2.sh|工具链/build2b.sh|g" "$f"
    sed -i "s|工具/装工具.sh|工具/装工具B.sh|g" "$f"
    sed -i "s|public static final int DEF_ID = 1;|public static final int DEF_ID = 101;|" "$f"
    sed -i "s|public static final int NID = 2;|public static final int NID = 102;|" "$f"
  done
  R1=$(grep -rEc "app\.reticle;" $W/src $W/AndroidManifest.xml | awk -F: '{s+=$2} END{print s+0}')
  R2=$(grep -rEc "8770" $W/src $W/AndroidManifest.xml | awk -F: '{s+=$2} END{print s+0}')
  R3=$(grep -rEc "/sdcard/Reticle($|[^B])" $W/src | awk -F: '{s+=$2} END{print s+0}')
  echo "   残留检查：旧包名 $R1 处｜旧端口 $R2 处｜旧目录 $R3 处（应全 0）"
  if [ "$R1" != "0" ] || [ "$R2" != "0" ] || [ "$R3" != "0" ]; then
    echo "！参数化自检不过："
    grep -rEn "app\.reticle;|8770|/sdcard/Reticle($|[^B])" $W/src $W/AndroidManifest.xml | head -20
    exit 8
  fi
  echo "   身份自检：过 ✓（包名 $(grep -m1 'package=' $W/AndroidManifest.xml | sed 's/.*package="//; s/".*//')）"
fi

# ── 78 号单：工程 libs（手动 jar / AAR）──
step "1) 工程 libs（jar / AAR）"
LIBJARS=""
AARRES=""
AARPKS=""
AARASSETS=""
AARJNI=""
if [ -d "$LIBS" ]; then
  for j in "$LIBS"/*.jar; do
    [ -e "$j" ] || continue
    LIBJARS="$LIBJARS $j"
    echo "   jar：$(basename "$j")（进 classpath 与 dex）"
  done
  for a in "$LIBS"/*.aar; do
    [ -e "$a" ] || continue
    n=$(basename "$a" .aar)
    D="$CACHE/aar/$n"
    K=$(fp "$a")
    if [ "$NOINCR" = "1" ] || [ ! -f "$D/.key" ] || [ "$(cat "$D/.key" 2>/dev/null)" != "$K" ]; then
      rm -rf "$D"; mkdir -p "$D"
      unzip -o -q "$a" -d "$D" || { echo "！AAR 解不开：$a"; exit 4; }
      echo "$K" > "$D/.key"
    fi
    [ -f "$D/classes.jar" ] && LIBJARS="$LIBJARS $D/classes.jar"
    if [ -d "$D/res" ]; then
      RK=$(fp "$D/res")
      if [ ! -f "$D/res.zip" ] || [ "$(cat "$D/.reskey" 2>/dev/null)" != "$RK" ]; then
        LD_LIBRARY_PATH="$LL" "$LL/aapt2" compile --dir "$D/res" -o "$D/res.zip" || { echo "！AAR 资源编译失败：$a"; exit 5; }
        echo "$RK" > "$D/.reskey"
      fi
      AARRES="$AARRES $D/res.zip"
    fi
    if [ -f "$D/AndroidManifest.xml" ]; then
      AP=$(grep -m1 -o 'package="[^"]*"' "$D/AndroidManifest.xml" | sed 's/package="//; s/"//') || true
      [ -n "$AP" ] && AARPKS="$AARPKS:$AP"
    fi
    [ -d "$D/assets" ] && AARASSETS="$AARASSETS $D"
    [ -d "$D/jni" ] && AARJNI="$AARJNI $D"
    echo "   aar：$(basename "$a")（classes.jar$([ -d "$D/res" ] && echo " ＋ 资源")$([ -d "$D/assets" ] && echo " ＋ assets")$([ -d "$D/jni" ] && echo " ＋ jni")）"
  done
  [ -z "$LIBJARS$AARRES$AARASSETS$AARJNI" ] && echo "   libs/ 里没有 jar／AAR（跳过）"
else
  echo "   （工程没有 libs/：跳过）"
fi

# ── aapt2 compile（资源没变就跳）──
step "2) aapt2 compile"
RESK=$(fp "$RESDIR" "$KEEP/res-zips")
if [ "$NOINCR" != "1" ] && [ -f "$CACHE/app.zip" ] && [ "$(cat "$CACHE/res.key" 2>/dev/null)" = "$RESK" ]; then
  echo "   资源没变 → 跳过 aapt2 compile（复用缓存 app.zip）"
  cp -p "$CACHE/app.zip" "$W/reszip/app.zip"
  skipped "aapt2-compile"
else
  LD_LIBRARY_PATH="$LL" "$LL/aapt2" compile --dir "$RESDIR" -o "$W/reszip/app.zip"
  cp "$W/reszip/app.zip" "$CACHE/app.zip"
  echo "$RESK" > "$CACHE/res.key"
  echo "   编译完成（已入缓存）"
fi
RESARGS="-R $W/reszip/app.zip"
for z in "$KEEP"/res-zips/*.zip; do [ -e "$z" ] || continue; RESARGS="$RESARGS -R $z"; done
for z in $AARRES; do RESARGS="$RESARGS -R $z"; done

# assets（第三方 ＋ 工程 ＋ AAR，要合并成一个目录时才合并）
ASSETSRC=""
[ -d "$KEEP/assets" ] && ASSETSRC="$ASSETSRC $KEEP/assets"
[ -d "$SB/assets" ] && ASSETSRC="$ASSETSRC $SB/assets"
for d in $AARASSETS; do ASSETSRC="$ASSETSRC $d/assets"; done
AASSET=""
if [ -n "$AARASSETS" ] || { [ -d "$SB/assets" ] && [ -d "$KEEP/assets" ]; }; then
  mkdir -p "$W/aassets"
  for d in $ASSETSRC; do cp -rp "$d/." "$W/aassets/" 2>/dev/null || true; done
  AASSET="-A $W/aassets"
  echo "   assets：已合并 $(echo $ASSETSRC | wc -w) 处 → 临时目录"
elif [ -d "$KEEP/assets" ]; then
  AASSET="-A $KEEP/assets"
fi

# 第三方库 R 类包名
PKGS=$(grep -h -o 'package="[^"]*"' "$KEEP"/manifest碎片/*.xml 2>/dev/null | sed 's/package="//; s/"//' | sort -u | tr '\n' ':' | sed 's/:$//') || true
PKGS="$PKGS$AARPKS"
PKGS=$(printf '%s' "$PKGS" | sed 's/^://')
EXTRA=""
[ -n "$PKGS" ] && EXTRA="--extra-packages $PKGS"
echo "   R 类包名 $(printf '%s' "$PKGS" | tr ':' '\n' | grep -c . || true) 个"

# ── aapt2 link（键没变就跳）──
step "3) aapt2 link"
LINKKEY="$(fp "$MANIFEST" "$CACHE/app.zip" "$KEEP/res-zips" "$JAR" $AARRES $ASSETSRC)-$(printf '%s' "$EXTRA" | sha256sum | cut -c1-8)"
if [ "$NOINCR" != "1" ] && [ -f "$CACHE/base.apk" ] && [ -d "$CACHE/gen" ] && [ "$(cat "$CACHE/link.key" 2>/dev/null)" = "$LINKKEY" ]; then
  echo "   资源／manifest／依赖包名／assets 都没变 → 跳过 aapt2 link（复用 base.apk 与 R 类）"
  cp "$CACHE/base.apk" "$W/base.apk"
  cp -rp "$CACHE/gen/." "$W/gen/" 2>/dev/null || true
  skipped "aapt2-link"
else
  LD_LIBRARY_PATH="$LL" "$LL/aapt2" link -o "$W/base.apk" --manifest "$MANIFEST" -I "$JAR" \
    --min-sdk-version 30 --target-sdk-version 35 --java $W/gen $EXTRA $RESARGS $AASSET --auto-add-overlay > $W/aapt2link.log 2>&1 || { echo "！aapt2 link 失败（末尾 30 行）："; tail -n 30 $W/aapt2link.log; exit 5; }
  ls -l $W/base.apk
  cp "$W/base.apk" "$CACHE/base.apk"
  rm -rf "$CACHE/gen"; mkdir -p "$CACHE/gen"; cp -rp "$W/gen/." "$CACHE/gen/" 2>/dev/null || true
  echo "$LINKKEY" > "$CACHE/link.key"
  echo "   （已入缓存：base.apk ＋ R 类）"
fi

# ── ECJ（java 没变就跳；变了只重编变化文件所在包）──
step "4) ECJ 编译"
CP="$JAR"
for j in "$KEEP"/classes/*.jar; do [ -e "$j" ] || continue; b=$(basename "$j"); skip_jar "$b" && continue; CP="$CP:$j"; done
for j in $LIBJARS; do CP="$CP:$j"; done
CUR="$W/src.cur"
  { find "$SRCDIR" -name '*.java' -printf '%p %s %T@\n' 2>/dev/null
    find "$W/gen" -name '*.java' -exec sha256sum {} + 2>/dev/null | awk '{print $2, $1}' | sort
  } | sort > "$CUR"
ALLJ=$(find "$SRCDIR" "$W/gen" -name '*.java' | wc -l)
CHANGED=""
if [ "$NOINCR" != "1" ] && [ -d "$CACHE/classes" ] && [ -f "$CACHE/src.list" ]; then
  CHANGED=$(diff "$CACHE/src.list" "$CUR" | awk '/^[-+]/{p=substr($1,2); if (p ~ /^\//) print p}' | sort -u) || true
fi
CHN=$(printf '%s\n' "$CHANGED" | grep -c . || true)
FULLECJ=0
[ "$NOINCR" = "1" ] && FULLECJ=1
[ ! -d "$CACHE/classes" ] && FULLECJ=1
[ "$CHN" -gt 0 ] && [ "$((CHN * 5))" -gt "$((ALLJ * 3))" ] && FULLECJ=1   # 变太多（>60%）不如全量
if [ "$FULLECJ" = "0" ] && [ "$CHN" = "0" ]; then
  echo "   java／R 类都没变 → 跳过 ECJ（复用缓存 $ALLJ 个 class）"
  cp -rp "$CACHE/classes/." "$W/classes/" 2>/dev/null || true
  skipped "ECJ"
else
  # 包名映射（一次 awk 扫全部 java）
  find "$SRCDIR" "$W/gen" -name '*.java' > "$W/alljava.list"
  awk 'FNR==1{f=FILENAME} /^package /{if(!(f in seen)){seen[f]=1; sub(/^package /,""); sub(/;.*/,""); print f" "$0}}' $(cat "$W/alljava.list") > "$W/pkgmap"
  if [ "$FULLECJ" = "1" ]; then
    echo "   全量编译（$ALLJ 个 java；变化 $CHN 个$([ "$NOINCR" = "1" ] && echo "／NOINCR")）"
    SRCS=""
    for f in $(find "$SRCDIR" -name '*.java'); do SRCS="$SRCS $f"; done
    GEN=""
    for f in $(find "$W/gen" -name '*.java'); do GEN="$GEN $f"; done
    rm -rf "$W/classes"; mkdir -p "$W/classes"
    CLASSPATH=$TC/dex/ecj.dex:$TC/ecj-res $APP /system/bin $ECJ \
      -source 1.8 -target 1.8 -nowarn -proc:none -d $W/classes -classpath "$CP" $SRCS $GEN > $W/ecj.log 2>&1 || { echo "！ECJ 编译失败（末尾 30 行）："; tail -n 30 $W/ecj.log; exit 7; }
  else
    # 包级增量：变化文件所在包的全部 java 一起编（常量内联多数发生在包内）
    printf '%s\n' "$CHANGED" | grep '\.java$' > "$W/chg.list" || true
    # 78：先判能不能「只编变化的文件」——变化行里出现 static final（编译期常量会被别的类内联）
    #     或 gen 的 R 类变了（R 常量同理）⇒ 保守退回「整包」；否则只重编变化文件（快得多）
    NEEDPKG=0
    FASTSRC=""
    while read f; do
      [ -n "$f" ] || continue
      case "$f" in
        "$SRCDIR"/*)
          rel=${f#$SRCDIR/}
          old="$CACHE/srcprev/$rel"
          if [ -f "$old" ]; then
            dl=$(diff "$old" "$f" | grep -E "^[-+][^-+]" || true)
            printf '%s' "$dl" | grep -qE "static[[:space:]]+final" && NEEDPKG=1
          fi
          FASTSRC="$FASTSRC $f"
          ;;
        *) NEEDPKG=1 ;;
      esac
    done < "$W/chg.list"
    if [ "$NEEDPKG" = "0" ]; then
      printf '%s\n' "$FASTSRC" | tr ' ' '\n' | grep . > "$W/incsrc.list"
      echo "   文件级增量：变化 $CHN 个 java → 只重编 $(wc -l < "$W/incsrc.list") 个（变化行里没有 static final 常量、R 类没变）"
    else
      if [ -s "$W/chg.list" ]; then
        awk 'NR==FNR{chg[$1]=1;next} ($1 in chg){print $2}' "$W/chg.list" "$W/pkgmap" | sort -u > "$W/pkgs.list"
      else
        : > "$W/pkgs.list"
      fi
      awk 'NR==FNR{pk[$1]=1;next} ($2 in pk){print $1}' "$W/pkgs.list" "$W/pkgmap" > "$W/incsrc.list"
      echo "   包级增量：变化 $CHN 个 java／$(( $(wc -l < "$W/pkgs.list") )) 个包 → 重编 $(wc -l < "$W/incsrc.list") 个 java（原因：变化里有 static final 常量或 R 类变了）"
    fi
    INC=$(wc -l < "$W/incsrc.list")
    [ "$INC" = "0" ] && { echo "！增量清单是空的（变化 $CHN 个 java 却没算出该编谁）→ 停下，不产旧包"; exit 7; }
    cp -rp "$CACHE/classes/." "$W/classes/" 2>/dev/null || true
    # 源文件被删 ⇒ 顺手清掉它的 class（别留旧的）
    diff "$CACHE/src.list" "$CUR" | awk '/^-/{p=substr($1,2); if (p ~ /^\/.*\.java$/) print p}' > "$W/gone.list" || true
    while read f; do
      [ -n "$f" ] || continue
      n=$(basename "$f" .java)
      find "$W/classes" -name "$n*.class" -exec rm -f {} + 2>/dev/null || true
    done < "$W/gone.list"
    CLASSPATH=$TC/dex/ecj.dex:$TC/ecj-res $APP /system/bin $ECJ \
      -source 1.8 -target 1.8 -nowarn -proc:none -d $W/classes -classpath "$CP:$CACHE/classes" $(cat "$W/incsrc.list") > $W/ecj.log 2>&1 || { echo "！ECJ 编译失败（末尾 30 行）："; tail -n 30 $W/ecj.log; exit 7; }
  fi
  echo "   javac(ECJ) ok: $(find $W/classes -name '*.class' | wc -l) 个 class"
  rm -rf "$CACHE/classes"; mkdir -p "$CACHE/classes"; cp -rp "$W/classes/." "$CACHE/classes/"
  cp "$CUR" "$CACHE/src.list"
  # 78：留一份源码快照（下次判「变化行里有没有 static final」用；编译成功才更新）
  rm -rf "$CACHE/srcprev"; mkdir -p "$CACHE/srcprev"
  for f in $(find "$SRCDIR" -name '*.java'); do
    rel=${f#$SRCDIR/}
    mkdir -p "$CACHE/srcprev/$(dirname "$rel")"
    cp -p "$f" "$CACHE/srcprev/$rel"
  done
fi

# ── d8：依赖部分与工程部分分开，各自缓存 ──
step "5) d8"
LIBJARLIST=""
for j in "$KEEP"/classes/*.jar; do [ -e "$j" ] || continue; b=$(basename "$j"); skip_jar "$b" && continue; LIBJARLIST="$LIBJARLIST $j"; done
LIBJARLIST="$LIBJARLIST $LIBJARS"
LIBKEY="$(fp $LIBJARLIST "$JAR")-$(printf '%s' "$KEEP" | sha256sum | cut -c1-6)"
if [ "$NOINCR" != "1" ] && [ -f "$CACHE/dexlib/.key" ] && [ "$(cat "$CACHE/dexlib/.key" 2>/dev/null)" = "$LIBKEY" ] && [ -f "$CACHE/dexlib/classes.dex" ]; then
  echo "   依赖 jar 没变 → 跳过依赖部分 d8（复用 $(ls "$CACHE/dexlib"/classes*.dex | wc -l) 个 dex）"
  skipped "d8-依赖"
else
  rm -rf "$CACHE/dexlib"; mkdir -p "$CACHE/dexlib"
  CLASSPATH=$TC/dex/d8.dex:$TC/dex/d8-2.dex $APP -Xmx1536m /system/bin $D8M \
    --min-api 30 --lib "$JAR" --output "$CACHE/dexlib" $LIBJARLIST > $W/d8lib.log 2>&1 || { echo "！d8（依赖）失败（末尾 40 行）："; tail -n 40 $W/d8lib.log; exit 6; }
  echo "$LIBKEY" > "$CACHE/dexlib/.key"
  echo "   依赖部分 dex：$(ls "$CACHE/dexlib"/classes*.dex | wc -l) 个（已入缓存）"
fi
CLSK=$(fp "$W/classes")
if [ "$NOINCR" != "1" ] && [ -f "$CACHE/dexapp/.key" ] && [ "$(cat "$CACHE/dexapp/.key" 2>/dev/null)" = "$CLSK" ] && [ -f "$CACHE/dexapp/classes.dex" ]; then
  echo "   工程 class 没变 → 跳过工程部分 d8"
  skipped "d8-工程"
else
  rm -rf "$CACHE/dexapp"; mkdir -p "$CACHE/dexapp"
  D8IN=""
  for f in $(find $W/classes -name '*.class'); do D8IN="$D8IN $f"; done
  LIBOPT=""
  # 78：app 组 d8 不再把依赖 jar 当 --lib 传（读 60 个 jar 的类型信息最慢）；min-api 30 下缺类型只是 warning
  CLASSPATH=$TC/dex/d8.dex:$TC/dex/d8-2.dex $APP -Xmx1536m /system/bin $D8M \
    --min-api 30 --lib "$JAR" $LIBOPT --output "$CACHE/dexapp" $D8IN > $W/d8app.log 2>&1 || { echo "！d8（工程）失败（末尾 40 行）："; tail -n 40 $W/d8app.log; exit 6; }
  echo "$CLSK" > "$CACHE/dexapp/.key"
  echo "   工程部分 dex：$(ls "$CACHE/dexapp"/classes*.dex | wc -l) 个（已入缓存）"
fi

# ── 组装：工程 dex 在前（主 dex 要含 manifest 组件），依赖 dex 续号 ──
step "6) 组装"
rm -rf "$W/dex"; mkdir -p "$W/dex"
A=0
for f in "$CACHE/dexapp"/classes*.dex; do
  [ -e "$f" ] || continue
  A=$((A + 1))
  if [ "$A" = "1" ]; then ln -sf "$f" "$W/dex/classes.dex"; else ln -sf "$f" "$W/dex/classes$A.dex"; fi
done
K=0
for f in "$CACHE/dexlib"/classes*.dex; do
  [ -e "$f" ] || continue
  K=$((K + 1)); N=$((A + K))
  ln -sf "$f" "$W/dex/classes$N.dex"
done
echo "   dex：工程 $A 个 ＋ 依赖 $K 个（classes.dex 起连续编号）"
DEXCSV=""
for f in $W/dex/classes*.dex; do DEXCSV="$DEXCSV,$f"; done
DEXCSV=${DEXCSV#,}
JNI="-"
[ -d "$KEEP/jni" ] && JNI="$KEEP/jni"
[ -d "$SB/jni" ] && JNI="$SB/jni"
if [ -n "$AARJNI" ] || [ -d "$SB/jni" ]; then
  mkdir -p "$W/alibs"
  [ -d "$KEEP/jni" ] && cp -rp "$KEEP/jni/." "$W/alibs/"
  [ -d "$SB/jni" ] && cp -rp "$SB/jni/." "$W/alibs/"
  for d in $AARJNI; do mkdir -p "$W/alibs/lib"; cp -rp "$d/jni/." "$W/alibs/lib/" 2>/dev/null || true; done
  JNI="$W/alibs"
  echo "   jni：已合并（$JNI）"
fi
CLASSPATH=$TC/dex/buildtool.dex $APP /system/bin BuildTool $W/base.apk $W/unsigned.apk "$DEXCSV" "$JNI"
ls -l $W/unsigned.apk

# ── 签名 ──
step "7) 签名（release）"
KS="${KS:-/sdcard/手机通道/备份/Reticle-签名/reticle-release.p12}"
KD="${KD:-/sdcard/手机通道/备份/Reticle-签名/钥匙说明.txt}"
KA="${KA:-reticle}"
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
    --ks "${DEBUG_KS:-$TC/debug.p12}" --ks-type PKCS12 --ks-pass pass:android --key-pass pass:android \
    --out $W/signed.apk $W/unsigned.apk
fi
ls -l $W/signed.apk

step "7b) 自证：apksigner verify"
CLASSPATH=$TC/dex/apksigner.dex $APP /system/bin $SIGN verify --print-certs $W/signed.apk

step "8) 产物"
mkdir -p /sdcard/workbench/构建/输出; cp $W/signed.apk "/sdcard/workbench/构建/输出/$OUTNAME"
ls -l "/sdcard/workbench/构建/输出/$OUTNAME"; sha256sum "/sdcard/workbench/构建/输出/$OUTNAME"
echo "== 完 $(date '+%H:%M:%S') 总 $(( $(date +%s) - T0 ))s =="
[ -n "$SKIP" ] && echo "   本次跳过：$SKIP"
exit 0
