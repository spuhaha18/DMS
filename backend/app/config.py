from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    DATABASE_URL: str = "postgresql+psycopg://dms:dms@localhost:5432/dms"
    JWT_SECRET: str = ""
    JWT_ALGORITHM: str = "HS256"
    JWT_EXPIRE_HOURS: int = 8

    LDAP_HOST: str = "ldap://localhost"
    LDAP_DOMAIN: str = "example.local"
    LDAP_BASE_DN: str = "DC=example,DC=local"

    MINIO_ENDPOINT: str = "localhost:9000"
    MINIO_ACCESS_KEY: str = "CHANGE_ME"
    MINIO_SECRET_KEY: str = "CHANGE_ME"

    @field_validator("JWT_SECRET")
    @classmethod
    def jwt_secret_must_be_set(cls, v: str) -> str:
        if not v or len(v) < 32:
            raise ValueError("JWT_SECRET must be at least 32 characters")
        return v
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
