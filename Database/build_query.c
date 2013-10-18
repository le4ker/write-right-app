#include <stdio.h>

int main (void)
{
	int intDump, id = 0;
	char word[64], stringDump[64];
	
	while(scanf("%d %s %s %d", &intDump, word, stringDump, &intDump) != EOF)
	{
		printf("INSERT INTO Words VALUES (%d, '%s', 0, '0');\n", id++, word);
	}

	printf("INSERT INTO Essentials VALUES (0, 26, 0)\n");
	
	return 0;
}
