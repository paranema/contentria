"""로컬 Docker 이미지 실행 검증. 생성한 컨테이너·볼륨·네트워크는 종료 시 정리한다."""
import argparse
import base64
import json
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

BACKEND = Path(__file__).resolve().parents[2]
CONTEXTS = ("member", "publishing", "interaction", "notification", "media", "analytics")
APPS = ("api", "batch", "worker", "migrator")


def command(*args, input=None, check=True, timeout=300):
    result = subprocess.run(args, input=input, text=True, capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f"{args[0]} failed ({result.returncode}):\n{result.stdout[-3000:]}\n{result.stderr[-3000:]}")
    return result


def wait_for(description, check, timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if check():
                return
        except (urllib.error.URLError, OSError, ValueError):
            pass
        time.sleep(1)
    raise RuntimeError(f"Timed out: {description}")


def request(url, body=None):
    req = urllib.request.Request(url, data=None if body is None else json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=5) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build", action="store_true", help="실행 전 Dockerfile 4개로 이미지를 빌드한다")
    parser.add_argument("--tag", default="smoke", help="검증할 contentria/<app> 이미지 태그")
    args = parser.parse_args()
    if args.build:
        for app in APPS:
            print(f"Building {app}", flush=True)
            subprocess.run(["docker", "build", "-f", str(BACKEND / "apps" / app / "Dockerfile"),
                            "-t", f"contentria/{app}:{args.tag}", str(BACKEND)], check=True)
    for app in APPS:
        user = command("docker", "image", "inspect", f"contentria/{app}:{args.tag}",
                       "--format", "{{.Config.User}}").stdout.strip()
        assert user == "10001:10001", (app, user)

    prefix = "contentria-smoke-" + uuid.uuid4().hex[:10]
    containers = []
    password = secrets.token_hex(20)
    network = command("docker", "network", "create", "--label", "contentria.image-smoke=" + prefix, prefix).stdout.strip()

    def start(name, image, *arguments, env=None, app=False, options=(), copy_file=None):
        docker_args = ["docker", "create", "--name", f"{prefix}-{name}", "--network", network,
                       "--network-alias", name, "--label", "contentria.image-smoke=" + prefix]
        if app:
            docker_args += ["--read-only", "--tmpfs", "/tmp:rw,size=256m,mode=1777", "--cap-drop", "ALL",
                            "--security-opt", "no-new-privileges", "--memory", "768m", "--cpus", "2"]
        for key, value in (env or {}).items():
            docker_args += ["-e", f"{key}={value}"]
        identifier = command(*docker_args, *options, image, *arguments).stdout.strip()
        containers.append(identifier)
        if copy_file is not None:
            command("docker", "cp", str(copy_file), f"{identifier}:/sample.mp4")
        command("docker", "start", identifier)
        return identifier

    def finish(identifier, expected=0, timeout=180):
        result = int(command("docker", "wait", identifier, timeout=timeout).stdout.strip())
        if result not in (expected if isinstance(expected, tuple) else (expected,)):
            logs = command("docker", "logs", "--tail", "60", identifier, check=False)
            raise RuntimeError(f"Container exited {result}, expected {expected}:\n{logs.stdout}\n{logs.stderr}")

    def sql(text, database="contentria_v2"):
        return command("docker", "exec", "-i", postgres, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres",
                       "-d", database, "-At", input=text).stdout.strip()

    def port(identifier, internal=8080):
        value = command("docker", "port", identifier, str(internal)).stdout.strip()
        return "http://" + value

    def mc(*arguments, options=(), check=True):
        return command("docker", "run", "--rm", "--network", network, "--read-only", "--tmpfs", "/tmp:mode=1777",
                       "-e", f"MC_HOST_smoke=http://smokeuser:{password}@minio:9000", *options,
                       "cgr.dev/chainguard/minio-client:latest", "--config-dir", "/tmp/mc", *arguments, check=check)

    try:
        print("Starting isolated PostgreSQL, Kafka, MinIO and queue fixture", flush=True)
        postgres = start("postgres", "postgres:17", env={"POSTGRES_PASSWORD": password})
        wait_for("PostgreSQL", lambda: command("docker", "exec", postgres, "pg_isready", "-U", "postgres", check=False).returncode == 0)
        role_names = [f"{context}_migrator" for context in CONTEXTS] + ["api_app", "batch_app", "worker_app"]
        setup = BACKEND.parent / "infra/database/contentria-v2"
        variables = [item for role in role_names for item in ("-v", f"{role}_password={password}")]
        command("docker", "exec", "-i", postgres, "psql", "-X", "-U", "postgres", "-v", "ON_ERROR_STOP=1", *variables,
                input=(setup / "01-database-and-roles.sql").read_text())
        sql((setup / "02-schemas-and-grants.sql").read_text())
        migration_env = {"MIGRATION_DATABASE_URL": "jdbc:postgresql://postgres:5432/contentria_v2"}
        for context in CONTEXTS:
            migration_env[context.upper() + "_MIGRATOR_USERNAME"] = context + "_migrator"
            migration_env[context.upper() + "_MIGRATOR_PASSWORD"] = password
        for index in range(2):
            finish(start(f"migrator-{index}", f"contentria/migrator:{args.tag}", env=migration_env, app=True))
        for context in CONTEXTS:
            assert int(sql(f"SELECT count(*) FROM {context}.flyway_schema_history WHERE success")) > 0
        print("migrator: all six schemas migrated; repeated run succeeded", flush=True)

        start("kafka", "apache/kafka:4.1.0", env={
            "KAFKA_NODE_ID": "1", "KAFKA_PROCESS_ROLES": "broker,controller",
            "KAFKA_LISTENERS": "PLAINTEXT://:9092,CONTROLLER://:9093",
            "KAFKA_ADVERTISED_LISTENERS": "PLAINTEXT://kafka:9092",
            "KAFKA_CONTROLLER_QUORUM_VOTERS": "1@kafka:9093",
            "KAFKA_CONTROLLER_LISTENER_NAMES": "CONTROLLER",
            "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP": "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT",
            "KAFKA_INTER_BROKER_LISTENER_NAME": "PLAINTEXT", "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR": "1",
            "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR": "1", "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR": "1",
            "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS": "0", "KAFKA_HEAP_OPTS": "-Xms256m -Xmx256m",
            "CLUSTER_ID": "MkU3OEVBNTcwNTJENDM2Qk"})
        start("minio", "cgr.dev/chainguard/minio:latest", "server", "/data", env={
            "MINIO_ROOT_USER": "smokeuser", "MINIO_ROOT_PASSWORD": password},
            options=("--tmpfs", "/data:rw,size=256m,mode=1777"))
        wait_for("MinIO", lambda: mc("ready", "smoke", check=False).returncode == 0)
        mc("mb", "smoke/media")
        queue = start("queue", "python:3.13-slim", "python", "-u", "/fixture/queue.py", options=(
            "-v", f"{Path(__file__).resolve().parent}:/fixture:ro", "-p", "127.0.0.1::8080"))
        queue_url = port(queue)
        wait_for("queue fixture", lambda: request(queue_url))
        common = {"DATABASE_URL": "jdbc:postgresql://postgres:5432/contentria_v2", "DATABASE_PASSWORD": password,
                  "MEDIA_PUBLIC_BASE_URL": "http://minio:9000/media", "R2_ENDPOINT": "http://minio:9000",
                  "R2_BUCKET_NAME": "media", "CONTENTRIA_MEDIA_STORAGE_REGION": "us-east-1", "R2_ACCESS_KEY_ID": "smokeuser", "R2_SECRET_ACCESS_KEY": password,
                  "JAVA_TOOL_OPTIONS": "-Xms64m -Xmx384m"}
        api_env = common | {"DATABASE_USERNAME": "api_app", "KAFKA_BOOTSTRAP_SERVERS": "kafka:9092",
                            "GOOGLE_CLIENT_ID": "smoke", "GOOGLE_CLIENT_SECRET": "smoke",
                            "MEMBER_ACCESS_TOKEN_SECRET": base64.b64encode(secrets.token_bytes(32)).decode(),
                            "ANALYTICS_VISITOR_KEY_SECRET": secrets.token_hex(32), "MEMBER_RECAPTCHA_MODE": "off",
                            "MEMBER_MAIL_MODE": "log", "MEMBER_COOKIE_SECURE": "false"}
        api = start("api", f"contentria/api:{args.tag}", env=api_env, app=True, options=("-p", "127.0.0.1::8080"))
        api_url = port(api)
        wait_for("API published posts", lambda: isinstance(request(api_url + "/api/posts"), dict))
        try:
            request(api_url + "/api/me/profile")
            raise AssertionError("Protected request was accepted without authentication")
        except urllib.error.HTTPError as error:
            assert error.code == 401
        print("api: public HTTP request 200, protected request 401", flush=True)
        command("docker", "stop", "--time", "20", api)
        finish(api, expected=(0, 143), timeout=30)

        for job in ("mediaImageCleanup", "mediaVideoCleanup", "analyticsDailyStatistics"):
            finish(start(job.lower(), f"contentria/batch:{args.tag}", f"--spring.batch.job.name={job}",
                         env=common | {"DATABASE_USERNAME": "batch_app"}, app=True))
        invalid = start("invalid-job", f"contentria/batch:{args.tag}", "--spring.batch.job.name=missingJob",
                        env=common | {"DATABASE_USERNAME": "batch_app"}, app=True)
        assert int(command("docker", "wait", invalid).stdout.strip()) != 0
        print("batch: three jobs exited 0; unknown job exited nonzero", flush=True)

        with tempfile.TemporaryDirectory(prefix="contentria-image-smoke-") as temporary:
            sample = Path(temporary) / "sample.mp4"
            encoder = start("encoder", f"contentria/worker:{args.tag}", "-hide_banner", "-loglevel", "error",
                            "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=24:duration=2",
                            "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
                            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "/tmp/sample.mp4",
                            options=("--entrypoint", "ffmpeg"))
            finish(encoder)
            command("docker", "cp", f"{encoder}:/tmp/sample.mp4", str(sample))
            video_id = str(uuid.uuid4())
            key = f"raw/{video_id}/original.mp4"
            uploader = start("upload-sample", "cgr.dev/chainguard/minio-client:latest",
                             "--config-dir", "/tmp/mc", "cp", "/sample.mp4", "smoke/media/" + key,
                             env={"MC_HOST_smoke": f"http://smokeuser:{password}@minio:9000"}, copy_file=sample)
            finish(uploader)
            sql(f"""INSERT INTO media.videos (id,uploader_id,format,size_bytes,file_name,raw_key,status,unattached_since,created_at,updated_at)
                VALUES ('{video_id}','{uuid.uuid4()}','MP4',{sample.stat().st_size},'sample.mp4','{key}','PENDING',now(),now(),now())""")
            request(queue_url + "/enqueue", {"action": "PutObject", "object": {"key": key}})
            worker = start("worker", f"contentria/worker:{args.tag}", env=common | {
                "DATABASE_USERNAME": "worker_app", "CLOUDFLARE_ACCOUNT_ID": "smoke", "CF_QUEUE_ID": "smoke",
                "CF_API_TOKEN": password, "WORKER_POLL_INTERVAL": "1s",
                "CONTENTRIA_MEDIA_WORKER_QUEUE_BASEURL": "http://queue:8080"}, app=True)
            wait_for("video transcoding", lambda: sql(f"SELECT status FROM media.videos WHERE id='{video_id}'") == "COMPLETED", timeout=180)
            wait_for("queue ack", lambda: request(queue_url)["acked"])
            master_key = sql(f"SELECT master_key FROM media.videos WHERE id='{video_id}'")
            poster_key = sql(f"SELECT poster_key FROM media.videos WHERE id='{video_id}'")
            assert "#EXTM3U" in mc("cat", "smoke/media/" + master_key).stdout
            mc("stat", "smoke/media/" + poster_key)
            assert mc("stat", "smoke/media/" + key, check=False).returncode != 0
            command("docker", "stop", "--time", "20", worker)
            finish(worker, expected=(0, 143), timeout=30)
        print("worker: encoded H.264/AAC, generated HLS and poster, removed original, acknowledged queue, stopped cleanly", flush=True)
        print("All four images passed (non-root, read-only root filesystem, temporary /tmp)", flush=True)
    except BaseException:
        for identifier in containers:
            logs = command("docker", "logs", "--tail", "20", identifier, check=False)
            print(f"Container {identifier[:12]}:\n{logs.stdout}\n{logs.stderr}", flush=True)
        raise
    finally:
        for identifier in reversed(containers):
            command("docker", "rm", "--force", "--volumes", identifier, check=False)
        command("docker", "network", "rm", network, check=False)
        print("Removed this run's containers, anonymous volumes and network", flush=True)


if __name__ == "__main__":
    main()
