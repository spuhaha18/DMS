class DMSException(Exception):
    def __init__(self, error_code: str, message: str, status_code: int = 400, detail=None):
        self.error_code = error_code
        self.message = message
        self.status_code = status_code
        self.detail = detail
