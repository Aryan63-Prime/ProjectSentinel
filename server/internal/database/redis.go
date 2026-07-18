package database

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

const redisOperationTimeout = 2 * time.Second

var errRedisClientUnavailable = errors.New("redis client unavailable")

// RedisOptions contains Redis connection settings.
type RedisOptions struct {
	Host     string
	Port     string
	Password string
	DB       int
}

// RedisClient wraps the Redis connection used by repositories.
type RedisClient struct {
	address  string
	password string
	db       int
	client   *redis.Client
}

// NewRedisClient creates a Redis client.
func NewRedisClient(options RedisOptions) *RedisClient {
	address := fmt.Sprintf("%s:%s", options.Host, options.Port)

	return &RedisClient{
		address:  address,
		password: options.Password,
		db:       options.DB,
		client: redis.NewClient(&redis.Options{
			Addr:         address,
			Password:     options.Password,
			DB:           options.DB,
			DialTimeout:  redisOperationTimeout,
			ReadTimeout:  redisOperationTimeout,
			WriteTimeout: redisOperationTimeout,
		}),
	}
}

// Address returns the configured Redis address.
func (c *RedisClient) Address() string {
	return c.address
}

// DB returns the configured Redis database number.
func (c *RedisClient) DB() int {
	return c.db
}

// HasPassword reports whether a Redis password was configured.
func (c *RedisClient) HasPassword() bool {
	return c.password != ""
}

// Ping verifies that Redis is reachable.
func (c *RedisClient) Ping(ctx context.Context) error {
	if c == nil || c.client == nil {
		return errRedisClientUnavailable
	}

	return c.client.Ping(ctx).Err()
}

// Set stores a binary value with an expiration.
func (c *RedisClient) Set(ctx context.Context, key string, value []byte, ttl time.Duration) error {
	if c == nil || c.client == nil {
		return errRedisClientUnavailable
	}

	return c.client.Set(ctx, key, value, ttl).Err()
}

// Get returns a binary value and reports whether the key exists.
func (c *RedisClient) Get(ctx context.Context, key string) ([]byte, bool, error) {
	if c == nil || c.client == nil {
		return nil, false, errRedisClientUnavailable
	}

	value, err := c.client.Get(ctx, key).Bytes()
	if errors.Is(err, redis.Nil) {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, err
	}

	return value, true, nil
}

// Delete removes a key from Redis.
func (c *RedisClient) Delete(ctx context.Context, key string) error {
	if c == nil || c.client == nil {
		return errRedisClientUnavailable
	}

	return c.client.Del(ctx, key).Err()
}

// Close releases the Redis client resources.
func (c *RedisClient) Close() error {
	if c == nil || c.client == nil {
		return nil
	}

	return c.client.Close()
}
