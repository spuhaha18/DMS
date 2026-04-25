from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    DATABASE_URL: str = "postgresql+psycopg://dms:dms@localhost:5432/dms"
    JWT_SECRET: str = "dev-secret-change-in-production"
    JWT_ALGORITHM: str = "HS256"
    JWT_EXPIRE_HOURS: int = 8

    LDAP_HOST: str = "ldap://localhost"
    LDAP_DOMAIN: str = "example.local"
    LDAP_BASE_DN: str = "DC=example,DC=local"

    MINIO_ENDPOINT: str = "localhost:9000"
    MINIO_ACCESS_KEY: str = "minio"
    MINIO_SECRET_KEY: str = "minio12345"
    BUCKET_FINAL: str = "dms-final"
    BUCKET_SOURCE: str = "dms-source"
    BUCKET_TEMPLATES: str = "dms-templates"

    SMTP_HOST: str = "localhost"
    SMTP_PORT: int = 587
    SMTP_USER: str = ""
    SMTP_PASSWORD: str = ""
    SMTP_FROM: str = "dms@example.com"
    SMTP_TLS: bool = True

    FRONTEND_URL: str = "http://localhost:3000"
    RETENTION_YEARS: int = 10


settings = Settings()
