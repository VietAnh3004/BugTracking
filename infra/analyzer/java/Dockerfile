# evidence/analyzer-java — JDK 21 + Maven + SpotBugs CLI + find-sec-bugs + canary + seed .m2
#
# JDK 21 là BẮT BUỘC, không phải lựa chọn phiên bản tuỳ ý: SpotBugs trên JDK mới hơn trả về 0
# cảnh báo mà VẪN báo thành công (ADR 0003). Image này chốt cứng phiên bản để kết quả không phụ
# thuộc JDK của máy chủ — và `analyze.sh` còn kiểm lại lần nữa trên dữ liệu thật
# (FindBugsSummary/@java_version).

FROM maven:3.9-eclipse-temurin-21

ARG SPOTBUGS_VERSION=4.9.3
ARG FINDSECBUGS_VERSION=1.13.0

# Bản phân phối chính thức của SpotBugs, KHÔNG phải jar trần trên Maven Central: jar trần thiếu
# asm/dom4j/commons-* nên không chạy độc lập được (đã kiểm trong docs/spike-spotbugs-cli.md).
RUN set -eux; \
    curl -fsSL -o /tmp/spotbugs.tgz \
        "https://github.com/spotbugs/spotbugs/releases/download/${SPOTBUGS_VERSION}/spotbugs-${SPOTBUGS_VERSION}.tgz"; \
    mkdir -p /opt/spotbugs; \
    tar -xzf /tmp/spotbugs.tgz -C /opt/spotbugs --strip-components=1; \
    rm /tmp/spotbugs.tgz; \
    curl -fsSL -o /opt/spotbugs/plugin/findsecbugs-plugin.jar \
        "https://repo1.maven.org/maven2/com/h3xstream/findsecbugs/findsecbugs-plugin/${FINDSECBUGS_VERSION}/findsecbugs-plugin-${FINDSECBUGS_VERSION}.jar"

# Canary: mã mồi có lỗi biết trước, biên dịch sẵn để đưa vào CÙNG lời gọi SpotBugs với mã người
# dùng. Xem Javadoc trong Canary.java để biết vì sao nó không thay thế được các tầng kiểm khác.
COPY canary/Canary.java /opt/canary/src/evidence/canary/Canary.java
RUN mkdir -p /opt/canary/classes \
    && javac -d /opt/canary/classes /opt/canary/src/evidence/canary/Canary.java

# Seed .m2 CHỈ ĐỌC. Không dùng volume .m2 chung giữa các lần chạy (ADR 0008 mục 5: repo A khai
# <repositories> lạ, tải jar về cache chung, repo B nhận jar của kẻ tấn công mà không hề gọi
# mạng). Seed nằm trong image nên bất biến và không ai ghi vào được.
#
# Vai trò: làm pha `resolve` gần như tức thời cho dự án Spring Boot điển hình, VÀ là phương án dự
# phòng khi máy chủ không có mạng ra ngoài.
COPY seed-pom.xml /opt/m2-seed-build/pom.xml
RUN set -eux; \
    cd /opt/m2-seed-build; \
    mvn -B -q -Dmaven.repo.local=/opt/m2-seed dependency:go-offline; \
    mvn -B -q -Dmaven.repo.local=/opt/m2-seed \
        org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get \
        -Dartifact=org.apache.maven.plugins:maven-surefire-plugin:3.5.3 \
        -Dartifact=org.jacoco:jacoco-maven-plugin:0.8.12 || true; \
    rm -rf /opt/m2-seed-build; \
    chmod -R a-w /opt/m2-seed

# Coverage THEO TỪNG TEST (xem pertest/PerTestListener.java). Tải jar và biên dịch NGAY LÚC DỰNG
# IMAGE — pha analyze chạy không mạng, nên mọi thứ nó cần phải có sẵn ở đây.
#   - pertest-listener.jar: gắn vào lượt chạy test qua -Dmaven.test.additionalClasspath, tự đăng
#     ký bằng ServiceLoader. Chỉ phụ thuộc API JUnit Platform lúc biên dịch; lúc chạy, API đó do
#     provider Surefire của chính dự án cung cấp.
#   - converter-classes: đọc các file .exec riêng của từng test bằng JaCoCo core.
ARG JACOCO_VERSION=0.8.12
ARG ASM_VERSION=9.7
ARG JUNIT_PLATFORM_VERSION=1.11.4
COPY pertest/ /opt/pertest/src/evidence/pertest/
RUN set -eux; \
    M=https://repo1.maven.org/maven2; \
    mkdir -p /opt/pertest/lib /opt/pertest/compile /opt/pertest/listener-classes/META-INF/services /opt/pertest/converter-classes; \
    for a in \
        "org/jacoco/org.jacoco.core/${JACOCO_VERSION}/org.jacoco.core-${JACOCO_VERSION}.jar" \
        "org/ow2/asm/asm/${ASM_VERSION}/asm-${ASM_VERSION}.jar" \
        "org/ow2/asm/asm-commons/${ASM_VERSION}/asm-commons-${ASM_VERSION}.jar" \
        "org/ow2/asm/asm-tree/${ASM_VERSION}/asm-tree-${ASM_VERSION}.jar"; do \
        curl -fsSL -o "/opt/pertest/lib/$(basename "$a")" "$M/$a"; \
    done; \
    for a in \
        "org/junit/platform/junit-platform-launcher/${JUNIT_PLATFORM_VERSION}/junit-platform-launcher-${JUNIT_PLATFORM_VERSION}.jar" \
        "org/junit/platform/junit-platform-engine/${JUNIT_PLATFORM_VERSION}/junit-platform-engine-${JUNIT_PLATFORM_VERSION}.jar" \
        "org/junit/platform/junit-platform-commons/${JUNIT_PLATFORM_VERSION}/junit-platform-commons-${JUNIT_PLATFORM_VERSION}.jar" \
        "org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar" \
        "org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar"; do \
        curl -fsSL -o "/opt/pertest/compile/$(basename "$a")" "$M/$a"; \
    done; \
    javac --release 17 -cp "/opt/pertest/compile/*" -d /opt/pertest/listener-classes \
        /opt/pertest/src/evidence/pertest/PerTestListener.java; \
    echo evidence.pertest.PerTestListener \
        > /opt/pertest/listener-classes/META-INF/services/org.junit.platform.launcher.TestExecutionListener; \
    jar cf /opt/pertest/pertest-listener.jar -C /opt/pertest/listener-classes .; \
    javac -cp "/opt/pertest/lib/*" -d /opt/pertest/converter-classes \
        /opt/pertest/src/evidence/pertest/PerTestCoverage.java; \
    rm -rf /opt/pertest/compile /opt/pertest/listener-classes

COPY analyze.sh /opt/analyze.sh
RUN chmod 0555 /opt/analyze.sh

# Non-root (ADR 0008 mục 5). UID cố định để `copyArchiveToContainer` ghi được vào /work.
RUN useradd --uid 10001 --create-home --shell /usr/sbin/nologin analyzer \
    && mkdir -p /work && chown analyzer:analyzer /work

# Image maven gốc đặt MAVEN_CONFIG=/root/.m2 và HOME=/root. Chạy non-root thì entrypoint của nó
# in "mkdir: cannot create directory '/root': Permission denied ... Carrying on" ở MỌI lần chạy.
# Vô hại nhưng phải dẹp: log nhiễu là chỗ để một lỗi thật lẫn vào mà không ai đọc tới.
ENV HOME=/home/analyzer
ENV MAVEN_CONFIG=/home/analyzer/.m2

USER analyzer
WORKDIR /work

# Không ENTRYPOINT: mỗi pha (`resolve` / `analyze`) là một lời gọi riêng với mức đặc quyền khác
# nhau, do SandboxRunner quyết định — xem ADR 0008 mục 4bis.
CMD ["/opt/analyze.sh", "--help"]
