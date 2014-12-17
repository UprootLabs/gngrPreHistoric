CREATE TABLE IF NOT EXISTS PERMISSIONS(
  frameHost               VARCHAR NOT NULL,
  requestHost             VARCHAR NOT NULL,
  permissions             INT4,
  PRIMARY KEY (frameHost, requestHost)
);
