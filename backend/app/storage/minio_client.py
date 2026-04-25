import threading
from io import BytesIO
from minio import Minio
from app.config import settings

_client: Minio | None = None
_lock = threading.Lock()


def get_client() -> Minio:
    global _client
    if _client is None:
        with _lock:
            if _client is None:
                _client = Minio(
                    settings.MINIO_ENDPOINT,
                    access_key=settings.MINIO_ACCESS_KEY,
                    secret_key=settings.MINIO_SECRET_KEY,
                    secure=False,
                )
    return _client


def ensure_buckets():
    client = get_client()
    for bucket in [settings.BUCKET_FINAL, settings.BUCKET_SOURCE, settings.BUCKET_TEMPLATES]:
        if not client.bucket_exists(bucket):
            client.make_bucket(bucket)


def put_object(bucket: str, key: str, data: bytes, content_type: str = "application/octet-stream"):
    client = get_client()
    client.put_object(bucket, key, BytesIO(data), len(data), content_type=content_type)


def get_object(bucket: str, key: str) -> bytes:
    client = get_client()
    response = client.get_object(bucket, key)
    try:
        return response.read()
    finally:
        response.close()
        response.release_conn()
