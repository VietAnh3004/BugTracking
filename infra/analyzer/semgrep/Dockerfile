# evidence/analyzer-semgrep — Semgrep kèm ruleset đã VENDOR nướng sẵn trong image.
#
# Vì sao nướng ruleset vào image thay vì `--config=p/java` như tài liệu Semgrep hướng dẫn: cờ đó
# tải rule từ registry qua mạng MỖI LẦN CHẠY. Trong sandbox `--network=none` nó sẽ hỏng; tệ hơn,
# ở môi trường CÓ mạng thì bộ rule đổi theo thời gian và hai lần chạy trên cùng một mã nguồn cho
# hai kết quả khác nhau — mất tính tái lập, thứ mà phần thực nghiệm của đồ án dựa vào.
#
# Ruleset lấy từ case-study/semgrep-rules/ (đã vendor và commit trong repo), nên phiên bản rule
# nằm trong lịch sử Git chứ không nằm trên một máy chủ của người khác.

FROM semgrep/semgrep:1.170.1

COPY p-java.yaml /opt/semgrep-rules/p-java.yaml

USER semgrep
WORKDIR /work

# Cố định `--metrics=off`: Semgrep mặc định gửi số liệu sử dụng về máy chủ của họ. Trong sandbox
# `--network=none` thì nó chỉ tốn thời gian chờ timeout, còn ở môi trường có mạng thì đó là dữ
# liệu về mã nguồn người dùng rời khỏi hệ thống mà không ai đồng ý.
ENTRYPOINT ["semgrep", "scan", \
            "--config=/opt/semgrep-rules/p-java.yaml", \
            "--sarif", "--output=/work/.analysis/semgrep.sarif", \
            "--metrics=off"]
CMD ["/work/src"]
