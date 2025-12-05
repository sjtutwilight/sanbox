-- Grant minimal privileges for mysqld_exporter to read status metrics
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'demo'@'%';
FLUSH PRIVILEGES;
