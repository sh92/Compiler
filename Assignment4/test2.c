
void setElt(int array[],int size){
    int i;
    i=0;
    while(i<size){
        array[i] = i;
        ++i;
    }
}

int getElt(int pos, int array[])
{
    return array[pos];
}

void main()
{
    int list[100];
    int element;

    setElt(list,100);
    element = getElt(33,list);
    write(element);
}
