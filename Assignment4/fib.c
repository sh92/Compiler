int foo(int n) {
	if (n < 3)
	    return 1;
	return foo(n - 1) + foo(n - 2);
}

int main()
{
   int x;
   x = 4;
   x = foo(4);
   write(x);
   return 0;
}