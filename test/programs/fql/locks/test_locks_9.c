int main()
{
    int p1;  // condition variable
    int lk1; // lock variable

    int p2;  // condition variable
    int lk2; // lock variable

    int p3;  // condition variable
    int lk3; // lock variable

    int p4;  // condition variable
    int lk4; // lock variable

    int p5;  // condition variable
    int lk5; // lock variable

    int p6;  // condition variable
    int lk6; // lock variable

    int p7;  // condition variable
    int lk7; // lock variable

    int p8;  // condition variable
    int lk8; // lock variable

    int p9;  // condition variable
    int lk9; // lock variable

    int __BLAST_NONDET;


    int cond;

    while(1) {
        cond = __BLAST_NONDET;
        if (cond == 0) {
            goto out;
        } else {}
        lk1 = 0; // initially lock is open

        lk2 = 0; // initially lock is open

        lk3 = 0; // initially lock is open

        lk4 = 0; // initially lock is open

        lk5 = 0; // initially lock is open

        lk6 = 0; // initially lock is open

        lk7 = 0; // initially lock is open

        lk8 = 0; // initially lock is open

        lk9 = 0; // initially lock is open


	p1 = __BLAST_NONDET;
	p2 = __BLAST_NONDET;
	p3 = __BLAST_NONDET;
	p4 = __BLAST_NONDET;
	p5 = __BLAST_NONDET;
	p6 = __BLAST_NONDET;
	p7 = __BLAST_NONDET;
	p8 = __BLAST_NONDET;
	p9 = __BLAST_NONDET;


    // lock phase
        if (p1 != 0) {
            lk1 = 1; // acquire lock
        } else {}

        if (p2 != 0) {
            lk2 = 1; // acquire lock
        } else {}

        if (p3 != 0) {
            lk3 = 1; // acquire lock
        } else {}

        if (p4 != 0) {
            lk4 = 1; // acquire lock
        } else {}

        if (p5 != 0) {
            lk5 = 1; // acquire lock
        } else {}

        if (p6 != 0) {
            lk6 = 1; // acquire lock
        } else {}

        if (p7 != 0) {
            lk7 = 1; // acquire lock
        } else {}

        if (p8 != 0) {
            lk8 = 1; // acquire lock
        } else {}

        if (p9 != 0) {
            lk9 = 1; // acquire lock
        } else {}


    // unlock phase
        if (p1 != 0) {
            if (lk1 != 1) goto ERROR; // assertion failure
            lk1 = 0;
        } else {}

        if (p2 != 0) {
            if (lk2 != 1) goto ERROR; // assertion failure
            lk2 = 0;
        } else {}

        if (p3 != 0) {
            if (lk3 != 1) goto ERROR; // assertion failure
            lk3 = 0;
        } else {}

        if (p4 != 0) {
            if (lk4 != 1) goto ERROR; // assertion failure
            lk4 = 0;
        } else {}

        if (p5 != 0) {
            if (lk5 != 1) goto ERROR; // assertion failure
            lk5 = 0;
        } else {}

        if (p6 != 0) {
            if (lk6 != 1) goto ERROR; // assertion failure
            lk6 = 0;
        } else {}

        if (p7 != 0) {
            if (lk7 != 1) goto ERROR; // assertion failure
            lk7 = 0;
        } else {}

        if (p8 != 0) {
            if (lk8 != 1) goto ERROR; // assertion failure
            lk8 = 0;
        } else {}

        if (p9 != 0) {
            if (lk9 != 1) goto ERROR; // assertion failure
            lk9 = 0;
        } else {}

    }
  out:
    return 0;
  ERROR:
    return 0;  
}

