#ifndef WAVHUFF_LAME_CONFIG
#define WAVHUFF_LAME_CONFIG
#include <stdint.h>
#define STDC_HEADERS 1
#define HAVE_STDINT_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_LIMITS_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_STRCHR 1
#define HAVE_MEMCPY 1
#define HAVE_LONG_DOUBLE 1
#define SIZEOF_SHORT 2
#define SIZEOF_INT 4
#define SIZEOF_LONG __SIZEOF_LONG__
#define SIZEOF_LONG_LONG 8
#define SIZEOF_FLOAT 4
#define SIZEOF_DOUBLE 8
#define SIZEOF_LONG_DOUBLE __SIZEOF_LONG_DOUBLE__
#define TAKEHIRO_IEEE754_HACK 1
#define USE_FAST_LOG 1
#define PACKAGE "lame"
#define VERSION "3.100"
typedef float ieee754_float32_t;
typedef double ieee754_float64_t;
typedef long double ieee854_float80_t;
#endif
