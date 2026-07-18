package auth

import "errors"

func IsAuthenticationError(err error) bool {
	return errors.Is(err, ErrMissingToken) || errors.Is(err, ErrInvalidToken)
}
