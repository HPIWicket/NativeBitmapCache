#include "ashmem.h"

#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <fcntl.h>

#include <linux/ashmem.h>

#define ASHMEM_DEVICE	"/dev/ashmem"
/*
 * ashmem_create_region - creates a new ashmem region and returns the file
 * descriptor, or <0 on error
 *
 * `name' is an optional label to give the region (visible in /proc/pid/maps)
 * `size' is the size of the region, in page-aligned bytes
 */
int ashmem_create_region(const char *name, size_t size)
{
	int fd, ret;

	fd = open(ASHMEM_DEVICE, O_RDWR);
	if (fd < 0)
		return fd;

	if (name) {
		char buf[ASHMEM_NAME_LEN];

		strlcpy(buf, name, sizeof(buf));
		ret = ioctl(fd, ASHMEM_SET_NAME, buf);
		if (ret < 0)
			goto error;
	}

	ret = ioctl(fd, ASHMEM_SET_SIZE, size);
	if (ret < 0)
		goto error;

	return fd;

error:
	close(fd);
	return ret;
}

int ashmem_set_prot_region(int fd, int prot)
{
	return ioctl(fd, ASHMEM_SET_PROT_MASK, prot);
}

int ashmem_pin_region(int fd, size_t offset, size_t len)
{
	struct ashmem_pin pin = { offset, len };
	return ioctl(fd, ASHMEM_PIN, &pin);
}

int ashmem_unpin_region(int fd, size_t offset, size_t len)
{
	struct ashmem_pin pin = { offset, len };
	return ioctl(fd, ASHMEM_UNPIN, &pin);
}
